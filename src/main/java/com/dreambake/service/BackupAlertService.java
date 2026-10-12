package com.dreambake.service;

import com.dreambake.entity.BackupRecord;
import com.dreambake.entity.EmailType;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Avisos por email cuando falla una copia de seguridad (paso 4.2).
 *
 * El error técnico (por ejemplo "mysqldump: Got error: 1045: Access denied") no le sirve a la dueña.
 * Por eso explain() lo traduce a lenguaje simple: qué pasó y qué revisar. El texto técnico va igual
 * al final del email, en chiquito, para quien tenga que arreglarlo.
 *
 * El email va a cada usuario ADMIN. Si no hay cuenta de correo configurada, EmailService no manda
 * nada (queda el registro en la auditoría y en el log).
 */
@Service
public class BackupAlertService {

    private static final Logger log = LoggerFactory.getLogger(BackupAlertService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UserRepository userRepository;
    private final EmailService emailService;

    public BackupAlertService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /** Explicación simple de un error: qué pasó y qué revisar. */
    public record Explanation(String what, String check) {}

    /** Falló el backup (no se guardó ninguna copia nueva). */
    public void backupFailed(BackupRecord r, String reason) {
        Explanation ex = explain(r.getErrorMessage());
        send("Falló la copia de seguridad de DreamBake",
                "No se pudo hacer la copia de seguridad " + BackupService.typeName(r.getType())
                        + " (" + reason + "). Las copias anteriores siguen guardadas y no se borró ninguna.",
                ex, r.getErrorMessage(), r);
    }

    /** El backup salió bien en la PC, pero no se pudo copiar a la carpeta externa (Google Drive). */
    public void offsiteFailed(BackupRecord r, String offsiteDir, String error) {
        Explanation ex = explain(error);
        send("La copia de seguridad no se pudo subir a Google Drive",
                "La copia de seguridad " + r.getFileName() + " se guardó bien en la computadora, pero no se pudo "
                        + "copiar a la carpeta externa (" + offsiteDir + "). Si la computadora se rompe, esa copia se pierde.",
                ex, error, r);
    }

    /**
     * Traduce los errores más comunes a lenguaje simple. Se buscan palabras clave en el mensaje técnico;
     * si no coincide ninguna, se da una explicación general.
     */
    static Explanation explain(String error) {
        String t = error == null ? "" : error.toLowerCase();
        if (t.contains("no se encontró la carpeta externa")) {
            return new Explanation("No se encontró la carpeta de Google Drive.",
                    "Fijate que Google Drive para escritorio esté abierto y con la sesión iniciada (el ícono de la nube "
                            + "al lado del reloj) y que la unidad (por ejemplo G:) aparezca en \"Este equipo\".");
        }
        if (t.contains("huella sha-256 no coincide")) {
            return new Explanation("La copia en Google Drive quedó distinta del original (se dañó al copiarse).",
                    "Suele ser un problema pasajero. Si se repite, revisá el espacio libre en Google Drive y en el disco.");
        }
        if (t.contains("dentro de la carpeta de backups")) {
            return new Explanation("La carpeta externa está mal configurada.",
                    "backup.offsite-directory tiene que ser otra carpeta (la de Google Drive), no la misma de los backups.");
        }
        if (t.contains("1045") || t.contains("access denied")) {
            return new Explanation("La base de datos rechazó el usuario o la contraseña.",
                    "Si cambiaron la contraseña de MySQL, hay que actualizarla también en la configuración del sistema.");
        }
        if (t.contains("2003") || t.contains("2002") || t.contains("can't connect")) {
            return new Explanation("La base de datos MySQL no estaba encendida.",
                    "Revisá que el servicio MySQL esté iniciado (normalmente arranca solo con la computadora).");
        }
        if (t.contains("no space") || t.contains("espacio") || t.contains("disk full")) {
            return new Explanation("No hay espacio libre en el disco.",
                    "Liberá espacio en el disco donde se guardan los backups (o en Google Drive).");
        }
        if (t.contains("accessdenied") || t.contains("being used by another process") || t.contains("otro proceso")) {
            return new Explanation("Windows no dejó escribir el archivo (permisos o archivo abierto).",
                    "Cerrá cualquier programa que tenga abierta la carpeta de backups y revisá que se pueda escribir en ella.");
        }
        if (t.contains("dump completed") || t.contains("vacío o incompleto")) {
            return new Explanation("La copia de la base de datos quedó incompleta.",
                    "Puede pasar si MySQL se apagó en el medio. Si se repite, revisá que MySQL esté funcionando bien.");
        }
        if (t.contains("tardó más de")) {
            return new Explanation("La copia tardó demasiado y se cortó.",
                    "Revisá que la computadora no haya estado muy cargada o suspendida a esa hora.");
        }
        return new Explanation("Ocurrió un error inesperado al hacer la copia.",
                "Pasale el detalle técnico de abajo a quien instaló el sistema.");
    }

    private void send(String subject, String summary, Explanation ex, String technical, BackupRecord r) {
        List<User> admins = userRepository.findByRoles("ADMIN");
        if (admins.isEmpty()) {
            log.warn("No hay administradores para avisar del problema con el backup.");
            return;
        }
        String when = r.getCreatedAt() == null ? "" : r.getCreatedAt().format(WHEN);
        for (User admin : admins) {
            emailService.enviar(admin.getEmail(), subject, "email/backup-alerta",
                    Map.of("nombre", firstName(admin.getName()), "titulo", subject, "resumen", summary,
                            "que", ex.what(), "revisar", ex.check(),
                            "tecnico", technical == null ? "" : technical, "fecha", when),
                    EmailType.BACKUP_ALERT, null, null);
        }
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().split("\\s+")[0];
    }
}
