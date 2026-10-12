package com.dreambake.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ejecuta los programas de MySQL que vienen con el servidor (pasos 4.1 y 4.3):
 *   - mysqldump: hace el volcado de la base (BackupService)
 *   - mysql:     carga un volcado en una base (RestoreService)
 *
 * Usa los mismos datos de conexión que la app (spring.datasource.*). La contraseña NUNCA va en la
 * línea de comandos (cualquier programa de la PC puede ver los comandos que se están ejecutando):
 * va en un archivo de opciones temporal que solo puede leer el usuario actual, y que se borra
 * siempre al terminar, aunque haya error.
 */
@Component
public class MysqlClient {

    private static final Logger log = LoggerFactory.getLogger(MysqlClient.class);

    private final Environment env;

    public MysqlClient(Environment env) {
        this.env = env;
    }

    /** Error "esperado" (mal configurado, el programa falló…): el mensaje se puede mostrar tal cual. */
    public static class ClientException extends Exception {
        public ClientException(String message) { super(message); }
    }

    /** Datos de conexión sacados de spring.datasource.* */
    public record Connection(String host, int port, String database, String user, String password) {}

    // ===================== Rutas de los programas =====================

    /** Ruta de mysqldump (backup.mysqldump-path). Vacía = backups desactivados. */
    public String mysqldumpPath() {
        return env.getProperty("backup.mysqldump-path", "").trim();
    }

    /**
     * Ruta del cliente mysql (para restaurar). Se puede fijar con backup.mysql-path; si está vacía se
     * usa el "mysql" que está en la misma carpeta que mysqldump (siempre vienen juntos).
     */
    public String mysqlPath() {
        String explicit = env.getProperty("backup.mysql-path", "").trim();
        if (!explicit.isEmpty()) return explicit;
        String dump = mysqldumpPath();
        if (dump.isEmpty()) return "";
        Path folder = Paths.get(dump).getParent();
        if (folder == null) return "";
        String name = dump.toLowerCase().endsWith(".exe") ? "mysql.exe" : "mysql";
        return folder.resolve(name).toString();
    }

    // ===================== Conexión =====================

    public Connection connection() throws ClientException {
        String url = env.getProperty("spring.datasource.url", "");
        // Formato: jdbc:mysql://host[:puerto]/base[?opciones]
        Matcher m = Pattern.compile("^jdbc:mysql://([^/:?]+)(?::(\\d+))?/([^?;]+)").matcher(url);
        if (!m.find()) throw new ClientException("No se entiende la dirección de la base (spring.datasource.url).");
        int port = m.group(2) == null ? 3306 : Integer.parseInt(m.group(2));
        return new Connection(m.group(1), port, m.group(3),
                env.getProperty("spring.datasource.username", ""),
                env.getProperty("spring.datasource.password", ""));
    }

    // ===================== Ejecutar un programa =====================

    /**
     * Ejecuta program (mysqldump o mysql) con la conexión de la app y los argumentos indicados.
     * stdin puede ser null (si no, el programa lee ese archivo, por ejemplo un volcado para cargar).
     * La salida va a stdout y los errores a stderr (archivos: si se leyeran por "tubería" y nadie los
     * consumiera, el proceso podría quedarse trabado).
     * Si tarda más de timeoutMinutes o termina con error, tira ClientException con el motivo.
     */
    public void run(String program, List<String> args, Path stdin, Path stdout, Path stderr,
                    long timeoutMinutes) throws Exception {
        Connection db = connection();
        String name = Paths.get(program).getFileName().toString().replaceFirst("(?i)\\.exe$", "");
        Path optionsFile = null;
        try {
            optionsFile = Files.createTempFile("dreambake-" + name + "-", ".cnf");
            restrictToOwner(optionsFile);
            Files.writeString(optionsFile, "[client]\n"
                    + "user=" + optionValue(db.user()) + "\n"
                    + "password=" + optionValue(db.password()) + "\n"
                    + "host=" + optionValue(db.host()) + "\n"
                    + "port=" + db.port() + "\n", StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(program);
            // --defaults-extra-file tiene que ser la PRIMERA opción (regla de MySQL)
            command.add("--defaults-extra-file=" + optionsFile.toAbsolutePath());
            command.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile());
            if (stdin != null) pb.redirectInput(stdin.toFile());
            Process process = pb.start();
            if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new ClientException(name + " tardó más de " + timeoutMinutes + " minutos y se cortó.");
            }
            if (process.exitValue() != 0) {
                String err = Files.exists(stderr) ? Files.readString(stderr, StandardCharsets.UTF_8).trim() : "";
                throw new ClientException(hidePassword(name + " terminó con error (código " + process.exitValue() + ")"
                        + (err.isEmpty() ? "." : ": " + err)));
            }
        } finally {
            if (optionsFile != null) {
                try {
                    Files.deleteIfExists(optionsFile);
                } catch (IOException e) {
                    log.warn("No se pudo borrar el archivo de opciones temporal {}: {}", optionsFile, e.getMessage());
                }
            }
        }
    }

    /** Por las dudas: la contraseña de la base nunca aparece en un mensaje de error guardado. */
    public String hidePassword(String text) {
        String password = env.getProperty("spring.datasource.password", "");
        if (text == null || password.isEmpty()) return text;
        return text.replace(password, "[oculto]");
    }

    // Valor entre comillas para el archivo de opciones de MySQL (escapando \ y ")
    static String optionValue(String value) {
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
}
