package com.dreambake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import com.dreambake.service.MailAccountService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, revisa qué claves externas están cargadas y avisa en el log cuáles
 * funciones quedan desactivadas por no tener su clave. Nunca tira error: una clave
 * vacía solo apaga esa función (por ejemplo, sin clave de Gemini no hay asistente).
 */
@Component
public class ConfigurationCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationCheck.class);

    private final Environment env;
    private final MailAccountService mailAccounts;

    public ConfigurationCheck(Environment env, MailAccountService mailAccounts) {
        this.env = env;
        this.mailAccounts = mailAccounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        // El correo se puede cargar desde el panel (Ajustes → Correo de envío) o desde este archivo
        mailAccounts.current().ifPresentOrElse(
                a -> log.info("Envío de mails: configurado ({}, desde {})", a.username(),
                        "panel".equals(a.source()) ? "el panel" : "config/application.properties"),
                () -> log.warn("Envío de mails DESACTIVADO: cargá la cuenta en Ajustes → Correo de envío"));
        aviso("Login con Google", "spring.security.oauth2.client.registration.google.client-id",
                "spring.security.oauth2.client.registration.google.client-secret");
        aviso("Login con Facebook", "spring.security.oauth2.client.registration.facebook.client-id",
                "spring.security.oauth2.client.registration.facebook.client-secret");
        aviso("Asistente Bakey (IA)", "bakey.gemini.api-key");
        aviso("Copias de seguridad", "backup.directory", "backup.mysqldump-path");
        log.info("Zona horaria de la app: {}", java.util.TimeZone.getDefault().getID());
    }

    // Si falta alguna de las claves, avisa que la función está desactivada y cuáles faltan
    private void aviso(String funcion, String... claves) {
        StringBuilder faltan = new StringBuilder();
        for (String clave : claves) {
            String valor = env.getProperty(clave);
            if (valor == null || valor.isBlank()) {
                if (faltan.length() > 0) faltan.append(", ");
                faltan.append(clave);
            }
        }
        if (faltan.length() > 0) {
            log.warn("{} DESACTIVADO: falta configurar {} en config/application.properties", funcion, faltan);
        } else {
            log.info("{}: configurado", funcion);
        }
    }
}
