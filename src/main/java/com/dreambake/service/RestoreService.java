package com.dreambake.service;

import com.dreambake.config.EmailLogSchemaFix;
import com.dreambake.config.EnumCheckSchemaFix;
import com.dreambake.config.MaintenanceMode;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.BackupRecord;
import com.dreambake.entity.BackupStatus;
import com.dreambake.entity.BackupType;
import com.dreambake.entity.User;
import com.dreambake.repository.BackupRecordRepository;
import com.dreambake.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Restaurar una copia de seguridad desde el panel (paso 4.3).
 *
 * Restaurar REEMPLAZA los datos actuales por los del backup, así que se hace con varias medidas de
 * seguridad, en este orden (si algo falla antes del paso 7, la base real no se toca):
 *
 *  1. Confirmación: el administrador tildó "Entiendo", escribió RESTAURAR y su contraseña
 *     (con límite de intentos: 5 errores → 15 minutos bloqueado).
 *  2. Huella: la huella SHA-256 del zip tiene que ser igual a la que se guardó al crearlo
 *     (si no, el archivo se dañó o alguien lo cambió).
 *  3. Prueba en una base aparte (<base>_restore_stage): se carga ahí el volcado con el programa mysql.
 *  4. Compatibilidad: todas las tablas actuales tienen que existir en el backup con al menos las mismas
 *     columnas. Un backup de una versión vieja del sistema no se puede restaurar desde el panel.
 *  5. Copia del estado actual: se hace un backup "antes de restaurar" (para poder deshacerlo).
 *  6. Modo mantenimiento: el resto del sitio responde "En mantenimiento" mientras dura.
 *  7. Cambio de tablas DE UNA SOLA VEZ: un único RENAME TABLE mueve las tablas actuales a una base
 *     aparte y las de prueba a su lugar. MySQL lo hace todo junto: si falla, no cambia nada.
 *
 * NO se restauran audit_logs (la auditoría no se puede borrar: se conserva entera y la restauración
 * queda anotada al final) ni backup_records (describe los archivos de backup que existen hoy).
 * Las fotos del backup se agregan a uploads/ solo si faltan: nunca se pisa ni se borra ninguna.
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    /** Palabra que hay que escribir para confirmar. */
    public static final String CONFIRM_WORD = "RESTAURAR";

    /** Tablas que NO se restauran (se quedan como están). */
    static final Set<String> NOT_RESTORED = Set.of("audit_logs", "backup_records");

    private static final long IMPORT_TIMEOUT_MINUTES = 30;
    // Máximo que espera el cambio de tablas si algún pedido justo las está usando (en segundos)
    private static final int RENAME_WAIT_SECONDS = 20;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern SAFE_NAME = Pattern.compile("\\w+");
    private static final Path UPLOADS = Paths.get("uploads");
    private static final Path SECRET_KEY = Paths.get("config", "secret.key");

    private final BackupService backupService;
    private final BackupRecordRepository repository;
    private final MysqlClient mysql;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttempts;
    private final AuditService auditService;
    private final MaintenanceMode maintenance;
    private final EnumCheckSchemaFix enumCheckFix;
    private final EmailLogSchemaFix emailLogFix;

    public RestoreService(BackupService backupService, BackupRecordRepository repository, MysqlClient mysql,
                          JdbcTemplate jdbc, PasswordEncoder passwordEncoder, UserRepository userRepository,
                          LoginAttemptService loginAttempts, AuditService auditService, MaintenanceMode maintenance,
                          EnumCheckSchemaFix enumCheckFix, EmailLogSchemaFix emailLogFix) {
        this.backupService = backupService;
        this.repository = repository;
        this.mysql = mysql;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.loginAttempts = loginAttempts;
        this.auditService = auditService;
        this.maintenance = maintenance;
        this.enumCheckFix = enumCheckFix;
        this.emailLogFix = emailLogFix;
    }

    /** Resultado para el panel: status es el código HTTP a devolver, message lo que se muestra. */
    public record Result(int status, String message, List<String> warnings) {
        public boolean ok() { return status == 200; }
    }

    // Error "esperado": el mensaje se muestra tal cual al administrador
    private static class RestoreException extends Exception {
        RestoreException(String message) { super(message); }
    }

    /**
     * Restaura el backup id. email es el administrador logueado e ip la de su conexión (para el
     * límite de intentos de contraseña).
     */
    public Result restore(Long id, String password, String confirm, boolean understood, String email, String ip) {
        // ---- 1. Confirmación (el servidor la vuelve a comprobar aunque el panel ya lo haga) ----
        if (!understood || !CONFIRM_WORD.equals(confirm == null ? "" : confirm.trim())) {
            return new Result(400, "Para restaurar tenés que tildar \"Entiendo\" y escribir " + CONFIRM_WORD + ".", List.of());
        }
        // Límite de intentos propio ("restore:" + email): no se mezcla con el del inicio de sesión
        String attemptsKey = "restore:" + email;
        if (loginAttempts.isBlocked(attemptsKey, ip)) {
            return new Result(429, "Escribiste mal la contraseña demasiadas veces. Esperá 15 minutos y probá de nuevo.", List.of());
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || password == null || !passwordEncoder.matches(password, user.getPassword())) {
            loginAttempts.loginFailed(attemptsKey, ip);
            auditService.log(AuditAction.RESTORE, AuditModule.SISTEMA, "Backup", id,
                    "Intentó restaurar una copia de seguridad con la contraseña incorrecta", null);
            return new Result(400, "La contraseña no es correcta.", List.of());
        }
        loginAttempts.loginSucceeded(attemptsKey, ip);

        if (!backupService.isConfigured()) {
            return new Result(400, "Las copias de seguridad no están configuradas.", List.of());
        }
        BackupRecord record = repository.findById(id).filter(r -> r.getStatus() == BackupStatus.OK).orElse(null);
        Path file = record == null ? null : backupService.fileOf(record).filter(Files::isRegularFile).orElse(null);
        if (file == null) {
            return new Result(404, "No se encontró esa copia de seguridad (puede que se haya borrado).", List.of());
        }

        // ---- Mismo bloqueo que los backups: nada de backups ni otra restauración mientras tanto ----
        try {
            return backupService.withLock(() -> doRestore(record, file))
                    .orElse(new Result(409, "Hay una copia de seguridad o una restauración en curso. Probá de nuevo en unos minutos.", List.of()));
        } catch (Exception e) {
            log.error("Error inesperado al restaurar: {}", e.getMessage());
            return new Result(500, "Ocurrió un error inesperado. No se restauró nada.", List.of());
        }
    }

    private Result doRestore(BackupRecord record, Path file) {
        long start = System.nanoTime();
        String backupDate = record.getCreatedAt() == null ? "" : record.getCreatedAt().format(WHEN);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("archivo", record.getFileName());
        details.put("tipo", BackupService.typeName(record.getType()));
        details.put("fechaDelBackup", backupDate);
        List<String> warnings = new ArrayList<>();

        String stamp = LocalDateTime.now().format(STAMP);
        Path base = backupService.base();
        Path sqlFile = base.resolve(".restaurando_" + stamp + ".sql");
        Path outFile = base.resolve(".restaurando_" + stamp + ".out");
        Path errFile = base.resolve(".restaurando_" + stamp + ".err");
        String database = null, stage = null, before = null;
        boolean swapped = false;

        try {
            database = mysql.connection().database();
            if (!SAFE_NAME.matcher(database).matches()) {
                throw new RestoreException("El nombre de la base tiene caracteres raros; restaurá a mano (ver docs/RESTAURAR-BACKUP.md).");
            }
            stage = database + "_restore_stage";
            before = database + "_before_restore";
            String mysqlPath = mysql.mysqlPath();
            if (mysqlPath.isEmpty() || !Files.isRegularFile(Paths.get(mysqlPath))) {
                throw new RestoreException("No se encontró el programa mysql (se busca al lado de mysqldump, o en backup.mysql-path).");
            }

            // ---- 2. Huella: el zip tiene que ser exactamente el que se guardó ----
            String actual = BackupService.sha256(file);
            String sideFile = readSha256File(BackupService.shaFileOf(file));
            if (!actual.equals(record.getSha256()) || (sideFile != null && !sideFile.equals(actual))) {
                throw new RestoreException("El archivo de la copia está dañado o fue modificado (la huella SHA-256 no coincide).");
            }

            // ---- 3. Volcado: se saca del zip y se comprueba que esté completo ----
            extractDump(file, sqlFile);
            BackupService.validateDump(sqlFile);

            // ---- 4. Prueba en una base aparte ----
            jdbc.execute("DROP DATABASE IF EXISTS `" + stage + "`");
            jdbc.execute("CREATE DATABASE `" + stage + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            mysql.run(mysqlPath, List.of("--default-character-set=utf8mb4", stage), sqlFile, outFile, errFile,
                    IMPORT_TIMEOUT_MINUTES);

            // ---- 5. Compatibilidad: cada tabla actual tiene que estar en el backup con todas sus columnas ----
            Map<String, Set<String>> realColumns = columns(database);
            NOT_RESTORED.forEach(realColumns::remove);
            Map<String, Set<String>> stageColumns = columns(stage);
            List<String> missing = new ArrayList<>();
            for (var entry : realColumns.entrySet()) {
                Set<String> inBackup = stageColumns.get(entry.getKey());
                if (inBackup == null) {
                    missing.add("tabla " + entry.getKey());
                    continue;
                }
                for (String col : entry.getValue()) {
                    if (!inBackup.contains(col)) missing.add(entry.getKey() + "." + col);
                }
            }
            if (!missing.isEmpty()) {
                details.put("faltaEnElBackup", missing.size() > 10 ? missing.subList(0, 10) : missing);
                throw new RestoreException("Esta copia es de una versión anterior del sistema (le falta "
                        + String.join(", ", missing.size() > 3 ? missing.subList(0, 3) : missing)
                        + (missing.size() > 3 ? "…" : "") + "). Restaurarla desde el panel no es seguro: "
                        + "seguí la guía docs/RESTAURAR-BACKUP.md.");
            }
            // Cuántas filas hay ahora y cuántas habrá después (para el resumen y la auditoría)
            Map<String, String> rows = new TreeMap<>();
            for (String table : realColumns.keySet()) {
                rows.put(table, count(database, table) + " → " + count(stage, table));
            }
            details.put("filas", rows);

            // ---- 6. Copia del estado actual, para poder deshacer ----
            BackupService.Outcome pre = backupService.runBackup(BackupType.PRE_RESTORE,
                    "antes de restaurar " + record.getFileName());
            if (!pre.ran() || pre.record() == null || pre.record().getStatus() != BackupStatus.OK) {
                String why = pre.record() != null ? pre.record().getErrorMessage() : pre.message();
                throw new RestoreException("No se pudo guardar la copia del estado actual, así que no se restauró nada: " + why);
            }
            details.put("copiaPrevia", pre.record().getFileName());

            // ---- 7. Modo mantenimiento + cambio de tablas de una sola vez ----
            maintenance.start();
            jdbc.execute("DROP DATABASE IF EXISTS `" + before + "`");
            jdbc.execute("CREATE DATABASE `" + before + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            swapTables(database, stage, before, new TreeSet<>(realColumns.keySet()));
            swapped = true;
            log.warn("Restauración: se reemplazaron {} tablas con la copia {}", realColumns.size(), record.getFileName());

            // ---- Después del cambio (nada de esto deshace la restauración si falla) ----
            runQuietly(() -> enumCheckFix.run(null));
            runQuietly(() -> emailLogFix.run(null));
            int photos = restoreMissingUploads(file, warnings);
            details.put("fotosAgregadas", photos);
            checkSecretKey(file, warnings);
            dropQuietly(before);
        } catch (Exception e) {
            String msg = e instanceof RestoreException || e instanceof MysqlClient.ClientException ? e.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            msg = mysql.hidePassword(msg);
            log.error("Falló la restauración de {}: {}", record.getFileName(), msg);
            details.put("error", msg);
            auditService.log(AuditAction.RESTORE, AuditModule.SISTEMA, "Backup", record.getId(),
                    "Falló la restauración de la copia " + record.getFileName() + ": " + msg, details);
            // Si el cambio de tablas no llegó a hacerse, la base real quedó exactamente como estaba
            return new Result(swapped ? 500 : 400, (swapped ? "" : "No se restauró nada. ") + msg, List.of());
        } finally {
            maintenance.stop();
            if (stage != null) dropQuietly(stage);
            if (before != null && !swapped) dropQuietly(before);
            for (Path p : List.of(sqlFile, outFile, errFile)) {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { /* temporal */ }
            }
        }

        details.put("duración", (System.nanoTime() - start) / 1_000_000 + " ms");
        if (!warnings.isEmpty()) details.put("avisos", warnings);
        auditService.log(AuditAction.RESTORE, AuditModule.SISTEMA, "Backup", record.getId(),
                "Restauró la copia de seguridad " + record.getFileName() + " (del " + backupDate + ")", details);
        return new Result(200, "Listo: los datos volvieron al " + backupDate + ". Volvé a ingresar.", warnings);
    }

    // ===================== Pasos =====================

    // Primer "token" del archivo .sha256 (formato "hash  nombre"), o null si no existe
    private static String readSha256File(Path shaFile) throws IOException {
        if (!Files.isRegularFile(shaFile)) return null;
        String text = Files.readString(shaFile, StandardCharsets.US_ASCII).trim();
        return text.isEmpty() ? null : text.split("\\s+")[0].toLowerCase();
    }

    // Saca basedatos.sql.gz del zip y lo descomprime en sqlFile
    private static void extractDump(Path zipFile, Path sqlFile) throws Exception {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry entry = zip.getEntry("basedatos.sql.gz");
            if (entry == null) throw new RestoreException("La copia no tiene la base de datos adentro (falta basedatos.sql.gz).");
            try (InputStream in = new GZIPInputStream(zip.getInputStream(entry))) {
                Files.copy(in, sqlFile);
            }
        }
    }

    // Columnas de cada tabla de una base (nombre de tabla → nombres de columnas)
    private Map<String, Set<String>> columns(String schema) {
        Map<String, Set<String>> result = new TreeMap<>();
        jdbc.query("SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS c "
                        + "JOIN information_schema.TABLES t USING (TABLE_SCHEMA, TABLE_NAME) "
                        + "WHERE c.TABLE_SCHEMA = ? AND t.TABLE_TYPE = 'BASE TABLE'",
                rs -> { result.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2)); },
                schema);
        return result;
    }

    private long count(String schema, String table) {
        if (!SAFE_NAME.matcher(table).matches()) return -1;
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM `" + schema + "`.`" + table + "`", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * El cambio de tablas: UN SOLO "RENAME TABLE" que primero mueve cada tabla actual a la base
     * "before" y después cada tabla restaurada a su lugar. MySQL lo hace todo junto (es atómico):
     * si algo falla, no cambia ninguna. Se mueven primero las actuales para que los nombres de las
     * claves foráneas (iguales en las dos bases) queden libres antes de que entren las nuevas.
     *
     * Se hace en una misma conexión, con un tiempo máximo de espera: si algún pedido que ya estaba en
     * curso está usando una tabla, MySQL espera hasta RENAME_WAIT_SECONDS y si no, cancela todo.
     */
    private void swapTables(String database, String stage, String before, SortedSet<String> tables) {
        String moveOut = tables.stream()
                .map(t -> "`" + database + "`.`" + t + "` TO `" + before + "`.`" + t + "`")
                .collect(Collectors.joining(", "));
        String moveIn = tables.stream()
                .map(t -> "`" + stage + "`.`" + t + "` TO `" + database + "`.`" + t + "`")
                .collect(Collectors.joining(", "));
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET SESSION lock_wait_timeout = " + RENAME_WAIT_SECONDS);
                try {
                    st.execute("RENAME TABLE " + moveOut + ", " + moveIn);
                } finally {
                    st.execute("SET SESSION lock_wait_timeout = DEFAULT");
                }
            }
            return null;
        });
    }

    /**
     * Fotos: las del backup que NO están en uploads/ se copian. Nunca se pisa ni se borra ninguna
     * (una foto de más no molesta; una borrada sí). Se ignora cualquier ruta que salga de uploads/
     * (por ejemplo "uploads/../../config/x"): así un zip manipulado no puede escribir en otro lado.
     */
    private static int restoreMissingUploads(Path zipFile, List<String> warnings) {
        Path uploads = UPLOADS.toAbsolutePath().normalize();
        int added = 0;
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory() || !entry.getName().startsWith("uploads/")) continue;
                Path target = uploads.resolve(entry.getName().substring("uploads/".length())).normalize();
                if (!target.startsWith(uploads) || target.equals(uploads) || Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target);
                }
                added++;
            }
        } catch (IOException e) {
            warnings.add("No se pudieron recuperar todas las fotos: " + e.getMessage());
        }
        return added;
    }

    // Si la clave de cifrado del backup es otra, la contraseña del correo restaurada no se puede leer
    private static void checkSecretKey(Path zipFile, List<String> warnings) {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry entry = zip.getEntry("config/secret.key");
            if (entry == null || !Files.isRegularFile(SECRET_KEY)) return;
            byte[] inBackup;
            try (InputStream in = zip.getInputStream(entry)) {
                inBackup = in.readAllBytes();
            }
            if (!Arrays.equals(inBackup, Files.readAllBytes(SECRET_KEY))) {
                warnings.add("La clave de cifrado de esta copia es distinta de la actual: volvé a escribir "
                        + "la contraseña del correo en Ajustes → Correo de envío.");
            }
        } catch (IOException e) {
            log.warn("No se pudo comparar la clave de cifrado: {}", e.getMessage());
        }
    }

    private void dropQuietly(String schema) {
        try {
            jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        } catch (Exception e) {
            log.warn("No se pudo borrar la base temporal {}: {}", schema, e.getMessage());
        }
    }

    private static void runQuietly(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("Paso posterior a la restauración con error: {}", e.getMessage());
        }
    }
}
