package com.dreambake.service;

import com.dreambake.entity.BusinessSetting;
import com.dreambake.repository.BusinessSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Properties;

/**
 * La cuenta de correo desde la que salen los emails del sistema.
 *
 * Se puede cargar de dos maneras (gana la primera que esté completa):
 *  1. Desde el panel: Ajustes → Correo de envío. Se guarda en la base (business_settings),
 *     con la contraseña CIFRADA (SecretCipher). Es la forma pensada para la dueña.
 *  2. Desde config/application.properties (spring.mail.*), como en el paso 1.1.
 *
 * Al guardar desde el panel se prueba la conexión con el servidor de correo: si Gmail
 * rechaza el usuario o la contraseña, no se guarda y se muestra por qué.
 */
@Service
public class MailAccountService {

    private static final Logger log = LoggerFactory.getLogger(MailAccountService.class);

    // Claves en business_settings
    static final String HOST = "mail.host", PORT = "mail.port", STARTTLS = "mail.starttls",
            USERNAME = "mail.username", PASSWORD = "mail.password";

    public static final String GMAIL_HOST = "smtp.gmail.com";
    public static final int GMAIL_PORT = 587;

    // La cuenta en uso: con qué se manda, desde qué email y de dónde salió ("panel" o "archivo")
    public record Account(JavaMailSender sender, String username, String source) {}

    // Lo que ve el panel (nunca incluye la contraseña)
    public record Info(boolean configured, String source, String username, String host, int port,
                       boolean starttls, boolean hasPassword, String problem) {}

    // Lo que manda el panel. password vacía = conservar la guardada.
    public record Request(String username, String password, String host, Integer port, Boolean starttls) {}

    private final BusinessSettingRepository settings;
    private final SecretCipher cipher;
    private final ObjectProvider<JavaMailSender> fileSender;   // el de spring.mail.* (puede no existir)
    private final String fileUsername;

    // Se arma una sola vez y se reutiliza; se borra al guardar o quitar la cuenta
    private volatile Account panelAccount;

    public MailAccountService(BusinessSettingRepository settings, SecretCipher cipher,
                              ObjectProvider<JavaMailSender> fileSender,
                              @Value("${spring.mail.username:}") String fileUsername) {
        this.settings = settings;
        this.cipher = cipher;
        this.fileSender = fileSender;
        this.fileUsername = fileUsername == null ? "" : fileUsername.trim();
    }

    private String get(String key) {
        return settings.findById(key).map(BusinessSetting::getValue).filter(v -> !v.isBlank()).orElse(null);
    }

    /** La cuenta en uso, o vacío si no hay ninguna configurada. */
    public Optional<Account> current() {
        Account cached = panelAccount;
        if (cached != null) return Optional.of(cached);
        String user = get(USERNAME), encrypted = get(PASSWORD);
        if (user != null && encrypted != null) {
            try {
                String host = Optional.ofNullable(get(HOST)).orElse(GMAIL_HOST);
                int port = Optional.ofNullable(get(PORT)).map(Integer::parseInt).orElse(GMAIL_PORT);
                boolean starttls = !"false".equals(get(STARTTLS));
                Account a = new Account(build(host, port, starttls, user, cipher.decrypt(encrypted)), user, "panel");
                panelAccount = a;
                return Optional.of(a);
            } catch (Exception e) {
                // Por ejemplo, se perdió config/secret.key: hay que volver a cargar la contraseña en el panel
                log.warn("La cuenta de correo del panel no se puede usar: {}", e.getMessage());
            }
        }
        JavaMailSender s = fileSender.getIfAvailable();
        if (s != null && !fileUsername.isBlank()) return Optional.of(new Account(s, fileUsername, "archivo"));
        return Optional.empty();
    }

    /** Datos para mostrar en Ajustes (sin la contraseña). */
    public Info info() {
        String user = get(USERNAME), encrypted = get(PASSWORD);
        String problem = null;
        if (user != null && encrypted != null) {
            try {
                cipher.decrypt(encrypted);
            } catch (Exception e) {
                problem = "La contraseña guardada ya no se puede leer (cambió la clave de cifrado). Volvé a escribirla.";
            }
        }
        Optional<Account> a = current();
        return new Info(a.isPresent(), a.map(Account::source).orElse(null), user,
                Optional.ofNullable(get(HOST)).orElse(GMAIL_HOST),
                Optional.ofNullable(get(PORT)).map(Integer::parseInt).orElse(GMAIL_PORT),
                !"false".equals(get(STARTTLS)), encrypted != null, problem);
    }

    /**
     * Prueba la cuenta y, si el servidor de correo la acepta, la guarda.
     * Devuelve null si salió bien, o el mensaje de error para mostrar.
     */
    public String save(Request req) {
        String user = req.username() == null ? "" : req.username().trim();
        if (!user.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || user.length() > 150) {
            return "Escribí el email completo de la cuenta (por ejemplo, dulcesuenos@gmail.com).";
        }
        String host = req.host() == null || req.host().isBlank() ? GMAIL_HOST : req.host().trim();
        if (!host.matches("^[A-Za-z0-9.-]{3,100}$")) return "Revisá el servidor de correo (por ejemplo, smtp.gmail.com).";
        int port = req.port() == null ? GMAIL_PORT : req.port();
        if (port < 1 || port > 65535) return "Revisá el puerto (Gmail usa 587).";
        boolean starttls = req.starttls() == null || req.starttls();

        // Contraseña: la nueva (sin espacios: Gmail la muestra en grupos de 4 letras) o la que ya estaba,
        // pero solo si es la misma cuenta (si cambia el email, hay que escribir la contraseña de la cuenta nueva)
        String password = req.password() == null ? "" : req.password().replaceAll("\\s", "");
        if (password.isEmpty()) {
            String saved = get(PASSWORD);
            if (saved == null || !user.equalsIgnoreCase(Optional.ofNullable(get(USERNAME)).orElse(""))) {
                return "Escribí la contraseña de aplicación de esta cuenta.";
            }
            try {
                password = cipher.decrypt(saved);
            } catch (Exception e) {
                return "La contraseña guardada ya no se puede leer. Volvé a escribirla.";
            }
        }
        if (password.length() > 200) return "La contraseña es demasiado larga.";

        // Se prueba la conexión antes de guardar (máximo 10 segundos)
        try {
            build(host, port, starttls, user, password).testConnection();
        } catch (Exception e) {
            log.warn("Prueba de la cuenta de correo {} en {}:{} rechazada: {}", user, host, port, e.getMessage());
            return explain(e);
        }

        settings.save(new BusinessSetting(USERNAME, user));
        settings.save(new BusinessSetting(PASSWORD, cipher.encrypt(password)));
        settings.save(new BusinessSetting(HOST, host));
        settings.save(new BusinessSetting(PORT, String.valueOf(port)));
        settings.save(new BusinessSetting(STARTTLS, String.valueOf(starttls)));
        panelAccount = null;
        log.info("Cuenta de correo del panel guardada: {} ({}:{}).", user, host, port);
        return null;
    }

    /** Quita la cuenta del panel (si hay una en config/application.properties, se vuelve a usar esa). */
    public void remove() {
        for (String k : new String[]{USERNAME, PASSWORD, HOST, PORT, STARTTLS}) {
            if (settings.existsById(k)) settings.deleteById(k);
        }
        panelAccount = null;
        log.info("Se quitó la cuenta de correo del panel.");
    }

    // Arma el "cartero" con los datos de la cuenta (mismos ajustes que spring.mail.* para Gmail)
    private static JavaMailSenderImpl build(String host, int port, boolean starttls, String user, String password) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(host);
        s.setPort(port);
        s.setUsername(user);
        s.setPassword(password);
        s.setDefaultEncoding("UTF-8");
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        p.put("mail.smtp.starttls.required", String.valueOf(starttls));
        // Si el servidor no responde, no esperar para siempre
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        return s;
    }

    // Los errores más comunes, en lenguaje simple
    static String explain(Exception e) {
        String t = (e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage())).toLowerCase();
        if (t.contains("535") || t.contains("authentication") || t.contains("username and password")) {
            return "Gmail rechazó el email o la contraseña. Tiene que ser una \"contraseña de aplicación\" (16 letras), no la contraseña normal de la cuenta.";
        }
        if (t.contains("unknownhost") || t.contains("couldn't connect") || t.contains("connect") || t.contains("timed out")) {
            return "No se pudo conectar con el servidor de correo. Revisá la conexión a internet y el servidor/puerto.";
        }
        return "El servidor de correo no aceptó la cuenta. Revisá los datos e intentá de nuevo.";
    }
}
