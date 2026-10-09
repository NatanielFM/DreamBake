package com.dreambake.service;

import com.dreambake.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Envío de emails HTML con el estilo de DreamBake.
 *
 * Cómo se usa desde otra clase:
 *
 *     emailService.enviar("clienta@mail.com", "Asunto", "email/prueba", Map.of("nombre", "Ana"), null);
 *
 * - "email/prueba" es una plantilla de src/main/resources/templates/email/. Todas usan el
 *   diseño común de email/base.html (logo, colores, pie con los datos del negocio).
 * - El método es @Async: se ejecuta en otro hilo y devuelve enseguida. Quien necesite saber
 *   si salió bien puede esperar el resultado (CompletableFuture); quien no, lo ignora.
 * - Nunca tira error hacia afuera: si el correo no está configurado o falla el servidor,
 *   se avisa en el log y el resultado queda como fallido (Resultado.ok = false).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Cómo salió un envío: ok = true si el servidor de correo aceptó el mensaje.
    // "mensaje" explica el problema en lenguaje simple cuando ok = false.
    public record Resultado(boolean ok, String mensaje) {
        static Resultado exito() { return new Resultado(true, "Email enviado."); }
        static Resultado fallo(String mensaje) { return new Resultado(false, mensaje); }
    }

    // ObjectProvider: Spring solo crea el JavaMailSender si spring.mail.host está configurado.
    // Con este "proveedor" la app arranca igual cuando no lo está.
    private final ObjectProvider<JavaMailSender> mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties app;
    private final String username;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, TemplateEngine templateEngine,
                        AppProperties app, @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.app = app;
        this.username = username == null ? "" : username.trim();
    }

    // ¿Están cargados los datos del correo saliente (servidor + usuario)?
    public boolean estaConfigurado() {
        return mailSender.getIfAvailable() != null && !username.isBlank();
    }

    /**
     * Envía un email HTML armado con una plantilla.
     *
     * @param destinatario email de quien lo recibe
     * @param asunto       asunto del mensaje
     * @param plantilla    nombre de la plantilla, por ejemplo "email/prueba"
     * @param variables    datos que usa la plantilla (por ejemplo "nombre"); puede ser null
     * @param bajaUrl      link para darse de baja de los mails promocionales; null si no corresponde
     */
    @Async
    public CompletableFuture<Resultado> enviar(String destinatario, String asunto, String plantilla,
                                               Map<String, Object> variables, String bajaUrl) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Email '{}' no enviado: no hay dirección de destino.", asunto);
            return CompletableFuture.completedFuture(Resultado.fallo("No hay una dirección de email de destino."));
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || username.isBlank()) {
            log.warn("Email '{}' para {} NO enviado: falta configurar spring.mail.* en config/application.properties.",
                    asunto, destinatario);
            return CompletableFuture.completedFuture(Resultado.fallo(
                    "El correo no está configurado. Cargá los datos del servidor de correo en config/application.properties."));
        }
        try {
            // 1. Se arma el HTML con la plantilla
            Context ctx = new Context();
            Map<String, Object> datos = variables == null ? new HashMap<>() : new HashMap<>(variables);
            ctx.setVariables(datos);
            ctx.setVariable("asunto", asunto);
            ctx.setVariable("negocio", app.getBusiness());
            ctx.setVariable("publicUrl", app.getPublicUrl());
            ctx.setVariable("bajaUrl", bajaUrl);
            String html = templateEngine.process(plantilla, ctx);

            // 2. Se arma el mensaje: remitente, destinatario, asunto, HTML y el logo incrustado
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            // Gmail solo deja enviar "desde" la cuenta autenticada: el remitente es siempre el usuario SMTP,
            // con el nombre del negocio. Si hay un mail del negocio distinto, se usa para las respuestas.
            helper.setFrom(username, app.getBusiness().getName());
            String respuestas = app.getBusiness().getEmail();
            if (!respuestas.isBlank()) helper.setReplyTo(respuestas);
            helper.setTo(destinatario.trim());
            helper.setSubject(asunto);
            helper.setText(html, true);
            // El logo va dentro del mensaje (cid:logo): así se ve aunque el sitio no sea público todavía
            helper.addInline("logo", new ClassPathResource("static/img/logo-email.png"));

            // 3. Se envía
            sender.send(mensaje);
            log.info("Email '{}' enviado a {}.", asunto, destinatario);
            return CompletableFuture.completedFuture(Resultado.exito());
        } catch (Exception e) {
            log.error("No se pudo enviar el email '{}' a {}: {}", asunto, destinatario, e.getMessage());
            return CompletableFuture.completedFuture(Resultado.fallo(explicar(e)));
        }
    }

    // Traduce los errores más comunes de Gmail a algo que se entienda
    private String explicar(Exception e) {
        String texto = (e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage())).toLowerCase();
        if (texto.contains("535") || texto.contains("authentication")) {
            return "El servidor de correo rechazó el usuario o la contraseña. Con Gmail hay que usar una contraseña de aplicación.";
        }
        if (texto.contains("connect") || texto.contains("timed out") || texto.contains("unknownhost")) {
            return "No se pudo conectar con el servidor de correo. Revisá spring.mail.host, el puerto y la conexión a internet.";
        }
        return "No se pudo enviar el email. Revisá el registro (log) de la app para ver el detalle.";
    }
}
