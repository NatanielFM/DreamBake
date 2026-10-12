package com.dreambake.service;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.BackupRecord;
import com.dreambake.entity.BackupStatus;
import com.dreambake.entity.BackupType;
import com.dreambake.repository.BackupRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Copias de seguridad (paso 4.1).
 *
 * Cada backup es un solo archivo dreambake_AAAA-MM-DD_HHmm.zip que contiene:
 *   - basedatos.sql.gz : toda la base de datos (volcado de mysqldump, comprimido)
 *   - uploads/...      : las fotos subidas desde el panel
 *   - config/secret.key: la clave que descifra la contraseña del correo guardada en la base
 *                        (sin ella, al restaurar hay que volver a escribir esa contraseña)
 * Al lado se guarda un archivo .sha256 con la "huella" del zip, para comprobar más adelante que
 * no se dañó.
 *
 * Carpetas dentro de backup.directory y cuántos backups se guardan en cada una (retención):
 *   diarios/   7   (uno por día, de la tarea de las 3:00)
 *   semanales/ 4   (copia de un diario: los domingos, o si el último semanal tiene 7 días o más)
 *   mensuales/ 12  (copia de un diario: el primero de cada mes que se haga)
 *   manuales/  10  (los que se piden a mano)
 *
 * Regla de oro: los backups viejos se borran SOLO si el nuevo salió bien.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    // Cuántos backups se guardan en cada carpeta
    private static final Map<BackupType, Integer> RETENTION = Map.of(
            BackupType.DAILY, 7, BackupType.WEEKLY, 4, BackupType.MONTHLY, 12, BackupType.MANUAL, 10);

    // Carpeta de cada tipo, dentro de backup.directory
    private static final Map<BackupType, String> FOLDER = Map.of(
            BackupType.DAILY, "diarios", BackupType.WEEKLY, "semanales",
            BackupType.MONTHLY, "mensuales", BackupType.MANUAL, "manuales");

    // Solo los archivos con este nombre exacto son backups. La retención nunca toca otros archivos.
    private static final Pattern BACKUP_NAME = Pattern.compile("dreambake_\\d{4}-\\d{2}-\\d{2}_\\d{4}(_\\d+)?\\.zip");

    private static final DateTimeFormatter NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    // Un volcado completo pesa bastante más que esto; menos de 1 KB = algo salió mal
    private static final long MIN_DUMP_BYTES = 1024;

    // Si mysqldump tarda más que esto, se lo corta (la base de la pastelería tarda segundos)
    private static final long DUMP_TIMEOUT_MINUTES = 30;

    // La carpeta de las fotos y la clave de cifrado (las mismas rutas que usan MvcConfig y SecretCipher)
    private static final Path UPLOADS = Paths.get("uploads");
    private static final Path SECRET_KEY = Paths.get("config", "secret.key");

    // Evita que dos backups corran a la vez (la tarea de las 3:00, la puesta al día y uno manual)
    private final ReentrantLock lock = new ReentrantLock();

    private final Environment env;
    private final BackupRecordRepository repository;
    private final AuditService auditService;

    public BackupService(Environment env, BackupRecordRepository repository, AuditService auditService) {
        this.env = env;
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Resultado de runBackup(). ran=false significa que no se intentó (no está configurado o ya hay
     * uno en curso); en ese caso record es null y message explica por qué.
     */
    public record Outcome(boolean ran, BackupRecord record, String message) {}

    // Error "esperado" dentro del backup: el mensaje se guarda tal cual en errorMessage
    private static class BackupException extends Exception {
        BackupException(String message) { super(message); }
    }

    // ===================== Configuración =====================

    private String directory() {
        return env.getProperty("backup.directory", "").trim();
    }

    private String mysqldumpPath() {
        return env.getProperty("backup.mysqldump-path", "").trim();
    }

    /** ¿Están cargadas las claves y existe mysqldump? Si no, los backups quedan desactivados. */
    public boolean isConfigured() {
        return !directory().isEmpty() && !mysqldumpPath().isEmpty() && Files.isRegularFile(Paths.get(mysqldumpPath()));
    }

    /** Último backup que salió bien (de cualquier tipo), si hay alguno. */
    public Optional<BackupRecord> lastSuccessful() {
        return repository.findTopByStatusOrderByCreatedAtDesc(BackupStatus.OK);
    }

    // ===================== Backup completo =====================

    /**
     * Hace un backup completo. type es DAILY (tarea automática) o MANUAL (pedido a mano).
     * reason es solo para el log y la auditoría ("tarea diaria de las 3:00", "pedido desde el panel"…).
     */
    public Outcome runBackup(BackupType type, String reason) {
        if (!isConfigured()) {
            String msg = "Copias de seguridad desactivadas: falta configurar backup.directory y backup.mysqldump-path "
                    + "en config/application.properties (o la ruta de mysqldump no existe).";
            log.warn(msg);
            return new Outcome(false, null, msg);
        }
        // tryLock: si ya hay un backup en curso, no se espera; se avisa y listo
        if (!lock.tryLock()) {
            return new Outcome(false, null, "Ya hay una copia de seguridad en curso. Probá de nuevo en unos minutos.");
        }
        try {
            return new Outcome(true, doBackup(type, reason), null);
        } finally {
            lock.unlock();
        }
    }

    private BackupRecord doBackup(BackupType type, String reason) {
        long start = System.nanoTime();
        Path base = Paths.get(directory()).toAbsolutePath().normalize();
        Path folder = base.resolve(FOLDER.get(type));
        LocalDateTime now = LocalDateTime.now();
        String stamp = now.format(NAME_STAMP);

        // Archivos de trabajo: se borran siempre al final (salga bien o mal)
        Path dumpFile = base.resolve(".trabajando_" + stamp + ".sql");
        Path errFile = base.resolve(".trabajando_" + stamp + ".err");
        Path partFile = null;
        Path createdZip = null;   // el zip ya terminado (para borrarlo si después no se puede registrar)

        BackupRecord record = new BackupRecord();
        record.setType(type);
        record.setCreatedAt(now);

        List<String> copies = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        try {
            Files.createDirectories(folder);

            // 1. Volcado de la base con mysqldump + comprobación
            runMysqldump(dumpFile, errFile);
            validateDump(dumpFile);

            // 2. Zip con la base comprimida, las fotos y la clave de cifrado.
            //    Se escribe como .part y se renombra recién al final: un zip a medio escribir
            //    nunca tiene el nombre de un backup válido.
            Path zipFile = uniqueName(folder, "dreambake_" + stamp);
            partFile = zipFile.resolveSibling(zipFile.getFileName() + ".part");
            writeZip(partFile, dumpFile, base);
            Files.move(partFile, zipFile, StandardCopyOption.ATOMIC_MOVE);
            partFile = null;
            createdZip = zipFile;

            // 3. Huella SHA-256, guardada al lado en formato "hash  nombre" (el de sha256sum)
            String hash = sha256(zipFile);
            Files.writeString(shaFileOf(zipFile), hash + "  " + zipFile.getFileName() + "\n", StandardCharsets.US_ASCII);

            record.setFileName(zipFile.getFileName().toString());
            record.setSizeBytes(Files.size(zipFile));
            record.setSha256(hash);
            record.setStatus(BackupStatus.OK);
            record.setDurationMs(elapsedMs(start));
            repository.save(record);

            // 4. Copias semanal y mensual (solo de los diarios). Cada una tiene su fila en backup_records.
            //    Si una copia falla, el diario igual quedó bien: se avisa en el log y se sigue.
            if (type == BackupType.DAILY) {
                LocalDate today = now.toLocalDate();
                try {
                    if (needsWeekly(today)) copies.add(copyTo(zipFile, base, BackupType.WEEKLY, record));
                    if (needsMonthly(today)) copies.add(copyTo(zipFile, base, BackupType.MONTHLY, record));
                } catch (IOException e) {
                    log.error("El backup diario quedó bien, pero no se pudo hacer la copia semanal/mensual: {}", e.getMessage());
                }
            }

            // 5. Retención: recién ahora (el backup nuevo ya está bien guardado) se borran los que sobran
            deleted.addAll(applyRetention(base, type));
            if (copies.contains("semanal")) deleted.addAll(applyRetention(base, BackupType.WEEKLY));
            if (copies.contains("mensual")) deleted.addAll(applyRetention(base, BackupType.MONTHLY));

            log.info("Copia de seguridad {} creada: {} ({}, {} ms){}", typeName(type), record.getFileName(),
                    humanSize(record.getSizeBytes()), record.getDurationMs(),
                    copies.isEmpty() ? "" : " + copia " + String.join(" y ", copies));
        } catch (Exception e) {
            // Falló: se guarda el motivo y NO se borra ningún backup viejo
            String msg = e instanceof BackupException ? e.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            // Si el zip se llegó a crear pero su fila no se pudo guardar (por ejemplo, se cayó la base),
            // se borra: no tiene que quedar ningún backup sin registrar.
            if (createdZip != null && record.getId() == null) {
                deleteQuietly(createdZip);
                deleteQuietly(shaFileOf(createdZip));
            }
            record.setStatus(BackupStatus.FAILED);
            record.setErrorMessage(cut(hidePassword(msg), 500));
            record.setDurationMs(elapsedMs(start));
            log.error("Falló la copia de seguridad {} ({}): {}", typeName(type), reason, record.getErrorMessage());
            try {
                repository.save(record);
            } catch (Exception saveError) {
                log.error("Tampoco se pudo guardar el registro del backup fallido: {}", saveError.getMessage());
            }
        } finally {
            deleteQuietly(dumpFile);
            deleteQuietly(errFile);
            if (partFile != null) deleteQuietly(partFile);
        }

        audit(record, reason, copies, deleted);
        return record;
    }

    // ===================== 1. mysqldump =====================

    private void runMysqldump(Path dumpFile, Path errFile) throws Exception {
        Connection db = connectionData();
        Path optionsFile = null;
        try {
            // La contraseña NO va en la línea de comandos (cualquier programa de la PC puede ver los
            // comandos que se están ejecutando). Va en un archivo de opciones temporal que solo puede
            // leer el usuario actual, y que se borra en el finally aunque haya error.
            optionsFile = Files.createTempFile("dreambake-mysqldump-", ".cnf");
            restrictToOwner(optionsFile);
            Files.writeString(optionsFile, "[client]\n"
                    + "user=" + optionValue(db.user()) + "\n"
                    + "password=" + optionValue(db.password()) + "\n"
                    + "host=" + optionValue(db.host()) + "\n"
                    + "port=" + db.port() + "\n", StandardCharsets.UTF_8);

            List<String> command = List.of(
                    mysqldumpPath(),
                    // --defaults-extra-file tiene que ser la PRIMERA opción (regla de MySQL)
                    "--defaults-extra-file=" + optionsFile.toAbsolutePath(),
                    "--single-transaction",              // copia consistente sin bloquear la base (tablas InnoDB)
                    "--routines",                         // incluye procedimientos y funciones
                    "--triggers",                         // incluye los triggers (ej: los que protegen audit_logs)
                    "--default-character-set=utf8mb4",   // tildes, ñ y emojis bien guardados
                    "--no-tablespaces",                   // evita pedir el permiso PROCESS (usuario restringido, paso 7.2)
                    db.database());

            // La salida (el volcado) va directo a un archivo y los errores a otro. Si se leyeran
            // por "tubería" y nadie los consumiera, el proceso podría quedarse trabado.
            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectOutput(dumpFile.toFile())
                    .redirectError(errFile.toFile());
            Process process = pb.start();
            if (!process.waitFor(DUMP_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new BackupException("mysqldump tardó más de " + DUMP_TIMEOUT_MINUTES + " minutos y se cortó.");
            }
            if (process.exitValue() != 0) {
                String err = Files.exists(errFile) ? Files.readString(errFile, StandardCharsets.UTF_8).trim() : "";
                throw new BackupException("mysqldump terminó con error (código " + process.exitValue() + ")"
                        + (err.isEmpty() ? "." : ": " + err));
            }
        } finally {
            if (optionsFile != null) deleteQuietly(optionsFile);
        }
    }

    // Datos de conexión: los mismos de la app (spring.datasource.*), sin claves nuevas
    private record Connection(String host, int port, String database, String user, String password) {}

    private Connection connectionData() throws BackupException {
        String url = env.getProperty("spring.datasource.url", "");
        // Formato: jdbc:mysql://host[:puerto]/base[?opciones]
        Matcher m = Pattern.compile("^jdbc:mysql://([^/:?]+)(?::(\\d+))?/([^?;]+)").matcher(url);
        if (!m.find()) throw new BackupException("No se entiende la dirección de la base (spring.datasource.url).");
        int port = m.group(2) == null ? 3306 : Integer.parseInt(m.group(2));
        return new Connection(m.group(1), port, m.group(3),
                env.getProperty("spring.datasource.username", ""),
                env.getProperty("spring.datasource.password", ""));
    }

    // Valor entre comillas para el archivo de opciones de MySQL (escapando \ y ")
    private static String optionValue(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // Deja el archivo legible solo por el usuario que corre la app
    private static void restrictToOwner(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {                                   // Linux / Mac
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {                                     // Windows: un solo permiso, para el dueño
            AclEntry onlyOwner = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.of(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.APPEND_DATA, AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.READ_NAMED_ATTRS,
                            AclEntryPermission.WRITE_NAMED_ATTRS, AclEntryPermission.READ_ACL,
                            AclEntryPermission.SYNCHRONIZE, AclEntryPermission.DELETE))
                    .build();
            acl.setAcl(List.of(onlyOwner));
        }
    }

    // ===================== Validación del volcado =====================

    /**
     * Un volcado completo pesa más de 1 KB y su última línea es "-- Dump completed on …".
     * Si mysqldump se cortó a la mitad (disco lleno, base caída), esa línea no está.
     */
    private static void validateDump(Path dumpFile) throws Exception {
        long size = Files.exists(dumpFile) ? Files.size(dumpFile) : 0;
        if (size <= MIN_DUMP_BYTES) {
            throw new BackupException("El volcado de la base está vacío o incompleto (" + size + " bytes).");
        }
        // Se lee solo el final del archivo (no hace falta cargar todo el volcado en memoria)
        try (RandomAccessFile raf = new RandomAccessFile(dumpFile.toFile(), "r")) {
            int len = (int) Math.min(512, size);
            byte[] tail = new byte[len];
            raf.seek(size - len);
            raf.readFully(tail);
            String text = new String(tail, StandardCharsets.UTF_8).strip();
            String lastLine = text.substring(text.lastIndexOf('\n') + 1).strip();
            if (!lastLine.startsWith("-- Dump completed")) {
                throw new BackupException("El volcado de la base no terminó bien (falta la línea \"-- Dump completed\").");
            }
        }
    }

    // ===================== 2. Zip =====================

    private static void writeZip(Path zipFile, Path dumpFile, Path backupBase) throws IOException {
        Path uploads = UPLOADS.toAbsolutePath().normalize();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Nada de esto se comprime de nuevo: el .gz ya está comprimido y las fotos (JPG, PNG, WEBP)
            // también. Comprimir otra vez solo gastaría tiempo.
            zip.setLevel(Deflater.NO_COMPRESSION);

            // La base, comprimida con gzip dentro del zip
            zip.putNextEntry(new ZipEntry("basedatos.sql.gz"));
            GZIPOutputStream gz = new GZIPOutputStream(new KeepOpen(zip));
            Files.copy(dumpFile, gz);
            gz.finish();               // termina el .gz sin cerrar el zip
            zip.closeEntry();

            // Las fotos subidas (si la carpeta existe)
            if (Files.isDirectory(uploads)) {
                try (Stream<Path> files = Files.walk(uploads)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (!Files.isRegularFile(file)) continue;
                        // Por si alguien configura la carpeta de backups dentro de uploads/
                        if (file.toAbsolutePath().normalize().startsWith(backupBase)) continue;
                        String name = "uploads/" + uploads.relativize(file).toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(name));
                        Files.copy(file, zip);
                        zip.closeEntry();
                    }
                }
            }

            // La clave de cifrado de la contraseña del correo (si existe)
            if (Files.isRegularFile(SECRET_KEY)) {
                zip.putNextEntry(new ZipEntry("config/secret.key"));
                Files.copy(SECRET_KEY, zip);
                zip.closeEntry();
            }
        }
    }

    // Envoltorio que no cierra el zip cuando se cierra el gzip de adentro
    private static class KeepOpen extends FilterOutputStream {
        KeepOpen(OutputStream out) { super(out); }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
        @Override public void close() throws IOException { flush(); }
    }

    // dreambake_2026-10-11_0300.zip; si ya existe (dos backups en el mismo minuto) → _2, _3…
    private static Path uniqueName(Path folder, String baseName) {
        Path candidate = folder.resolve(baseName + ".zip");
        for (int n = 2; Files.exists(candidate); n++) {
            candidate = folder.resolve(baseName + "_" + n + ".zip");
        }
        return candidate;
    }

    // ===================== 3. SHA-256 =====================

    // Huella del archivo: si cambia un solo byte del zip, la huella es totalmente distinta
    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path shaFileOf(Path zipFile) {
        return zipFile.resolveSibling(zipFile.getFileName() + ".sha256");
    }

    // ===================== 4. Copias semanal y mensual =====================

    // Semanal: los domingos, o si el último semanal tiene 7 días o más (por si la PC estuvo
    // apagada el domingo). Nunca dos el mismo día.
    private boolean needsWeekly(LocalDate today) {
        Optional<LocalDate> last = repository.findTopByTypeAndStatusOrderByCreatedAtDesc(BackupType.WEEKLY, BackupStatus.OK)
                .map(r -> r.getCreatedAt().toLocalDate());
        if (last.isEmpty()) return true;
        if (!last.get().isBefore(today)) return false;
        return today.getDayOfWeek() == DayOfWeek.SUNDAY || ChronoUnit.DAYS.between(last.get(), today) >= 7;
    }

    // Mensual: el primer backup de cada mes (normalmente el del día 1; si la PC estuvo apagada, el siguiente)
    private boolean needsMonthly(LocalDate today) {
        Optional<LocalDate> last = repository.findTopByTypeAndStatusOrderByCreatedAtDesc(BackupType.MONTHLY, BackupStatus.OK)
                .map(r -> r.getCreatedAt().toLocalDate());
        return last.isEmpty() || last.get().getYear() != today.getYear() || last.get().getMonth() != today.getMonth();
    }

    // Copia el zip y su .sha256 a la carpeta del tipo indicado y guarda su fila en backup_records
    private String copyTo(Path zipFile, Path base, BackupType type, BackupRecord original) throws IOException {
        Path folder = base.resolve(FOLDER.get(type));
        Files.createDirectories(folder);
        String baseName = zipFile.getFileName().toString().replaceFirst("\\.zip$", "");
        Path target = uniqueName(folder, baseName);
        Files.copy(zipFile, target);
        // El .sha256 se reescribe con el nombre de la copia (el hash es el mismo: el contenido es idéntico)
        Files.writeString(shaFileOf(target), original.getSha256() + "  " + target.getFileName() + "\n", StandardCharsets.US_ASCII);

        BackupRecord copy = new BackupRecord();
        copy.setType(type);
        copy.setStatus(BackupStatus.OK);
        copy.setFileName(target.getFileName().toString());
        copy.setSizeBytes(original.getSizeBytes());
        copy.setSha256(original.getSha256());
        copy.setDurationMs(0L);
        copy.setCreatedAt(original.getCreatedAt());
        repository.save(copy);
        return type == BackupType.WEEKLY ? "semanal" : "mensual";
    }

    // ===================== 5. Retención =====================

    /** Borra los backups más viejos de la carpeta del tipo, dejando los N más nuevos. Devuelve los borrados. */
    private List<String> applyRetention(Path base, BackupType type) {
        Path folder = base.resolve(FOLDER.get(type));
        int keep = RETENTION.get(type);
        List<String> deleted = new ArrayList<>();
        if (!Files.isDirectory(folder)) return deleted;
        try (Stream<Path> files = Files.list(folder)) {
            // Solo archivos con nombre de backup. Como la fecha va en el nombre (AAAA-MM-DD_HHmm),
            // ordenar por nombre = ordenar por fecha. Del más nuevo al más viejo.
            List<Path> backups = files
                    .filter(p -> Files.isRegularFile(p) && BACKUP_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (Path old : backups.subList(Math.min(keep, backups.size()), backups.size())) {
                Files.deleteIfExists(old);
                Files.deleteIfExists(shaFileOf(old));
                deleted.add(FOLDER.get(type) + "/" + old.getFileName());
            }
        } catch (IOException e) {
            // No es grave: el backup nuevo ya está guardado; se reintenta en el próximo
            log.warn("No se pudieron borrar backups viejos de {}: {}", folder, e.getMessage());
        }
        if (!deleted.isEmpty()) log.info("Retención: se borraron {}", deleted);
        return deleted;
    }

    // ===================== Auditoría =====================

    private void audit(BackupRecord r, String reason, List<String> copies, List<String> deleted) {
        String description = r.getStatus() == BackupStatus.OK
                ? "Copia de seguridad " + typeName(r.getType()) + " creada: " + r.getFileName()
                    + " (" + humanSize(r.getSizeBytes()) + ")"
                : "Falló la copia de seguridad " + typeName(r.getType()) + ": " + r.getErrorMessage();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tipo", typeName(r.getType()));
        details.put("motivo", reason);
        details.put("resultado", r.getStatus() == BackupStatus.OK ? "Correcto" : "Falló");
        if (r.getFileName() != null) details.put("archivo", r.getFileName());
        if (r.getSizeBytes() != null) details.put("tamaño", humanSize(r.getSizeBytes()));
        if (r.getSha256() != null) details.put("sha256", r.getSha256());
        details.put("duración", r.getDurationMs() + " ms");
        if (!copies.isEmpty()) details.put("copias", String.join(", ", copies));
        if (!deleted.isEmpty()) details.put("borradosPorRetención", deleted);
        if (r.getErrorMessage() != null) details.put("error", r.getErrorMessage());

        auditService.log(AuditAction.BACKUP, AuditModule.SISTEMA, "Backup", r.getId(), description, details);
    }

    // ===================== Utilidades =====================

    static String typeName(BackupType type) {
        return switch (type) {
            case DAILY -> "diaria";
            case WEEKLY -> "semanal";
            case MONTHLY -> "mensual";
            case MANUAL -> "manual";
        };
    }

    // 12.345.678 bytes → "11,8 MB" (formato argentino, con coma decimal)
    static String humanSize(Long bytes) {
        if (bytes == null) return "-";
        if (bytes < 1024) return bytes + " bytes";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.forLanguageTag("es-AR"), "%.1f KB", kb);
        return String.format(Locale.forLanguageTag("es-AR"), "%.1f MB", kb / 1024.0);
    }

    // Por las dudas: la contraseña de la base nunca aparece en un mensaje de error guardado
    private String hidePassword(String text) {
        String password = env.getProperty("spring.datasource.password", "");
        if (text == null || password.isEmpty()) return text;
        return text.replace(password, "[oculto]");
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo temporal {}: {}", file, e.getMessage());
        }
    }
}
