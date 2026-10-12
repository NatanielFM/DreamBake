package com.dreambake.service;

import com.dreambake.config.AppProperties;
import com.dreambake.entity.Customer;
import com.dreambake.entity.EmailLog;
import com.dreambake.entity.EmailStatus;
import com.dreambake.entity.EmailType;
import com.dreambake.repository.EmailLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Envío de emails HTML con el estilo de DreamBake.
 *
 * Cómo se usa desde otra clase:
 *
 *     emailService.enviar(cliente.getEmail(), "¡Feliz cumpleaños!", "email/cumpleanos",
 *                         Map.of("nombre", "Ana"), EmailType.BIRTHDAY, cliente, null);
 *
 * - "email/cumpleanos" es una plantilla de src/main/resources/templates/email/. Todas usan el
 *   diseño común de email/base.html (logo, colores, pie con los datos del negocio).
 * - El método es @Async: se ejecuta en otro hilo y devuelve enseguida. Quien necesite saber
 *   si salió bien puede esperar el resultado (CompletableFuture); quien no, lo ignora.
 * - Nunca tira error hacia afuera: si el correo no está configurado o falla el servidor,
 *   se avisa en el log y el resultado queda como fallido (Resultado.ok = false).
 * - Cada intento queda guardado en el historial (tabla email_logs), salga bien o mal.
 * - Los emails promocionales (cumpleaños y promociones) solo se mandan a clientes que
 *   aceptaron recibirlos, y llevan el link para darse de baja (Ley 25.326).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Mensaje cuando todavía no hay una cuenta de correo cargada
    public static final String NO_CONFIGURADO =
            "El correo no está configurado. Cargá la cuenta en Ajustes → Correo de envío.";

    // Cómo salió un envío: ok = true si el servidor de correo aceptó el mensaje.
    // "mensaje" explica el problema en lenguaje simple cuando ok = false.
    public record Resultado(boolean ok, String mensaje) {
        static Resultado exito() { return new Resultado(true, "Email enviado."); }
        static Resultado fallo(String mensaje) { return new Resultado(false, mensaje); }
    }

    // La cuenta de correo desde la que se manda: la del panel (Ajustes → Correo de envío)
    // o, si no hay, la de config/application.properties. Ver MailAccountService.
    private final MailAccountService mailAccounts;
    private final TemplateEngine templateEngine;
    private final AppProperties app;
    private final EmailLogRepository emailLogRepository;
    // Nombre, WhatsApp y email del negocio que se editan en el panel (paso 6.4)
    private final BusinessProfileService businessProfile;

    public EmailService(MailAccountService mailAccounts, TemplateEngine templateEngine,
                        AppProperties app, EmailLogRepository emailLogRepository,
                        BusinessProfileService businessProfile) {
        this.businessProfile = businessProfile;
        this.mailAccounts = mailAccounts;
        this.templateEngine = templateEngine;
        this.app = app;
        this.emailLogRepository = emailLogRepository;
    }

    // ¿Hay una cuenta de correo cargada para mandar emails?
    public boolean estaConfigurado() {
        return mailAccounts.current().isPresent();
    }

    // Link para darse de baja de un cliente: <dirección pública>/baja?token=<su código>
    public String bajaUrl(Customer cliente) {
        if (cliente == null || cliente.getUnsubscribeToken() == null) return null;
        return app.getPublicUrl() + "/baja?token=" + cliente.getUnsubscribeToken();
    }

    /**
     * Envía un email HTML armado con una plantilla y lo registra en el historial.
     *
     * @param destinatario email de quien lo recibe
     * @param asunto       asunto del mensaje
     * @param plantilla    nombre de la plantilla, por ejemplo "email/prueba"
     * @param variables    datos que usa la plantilla (por ejemplo "nombre"); puede ser null
     * @param tipo         tipo de email (cumpleaños, promoción, prueba…)
     * @param cliente      cliente al que va dirigido; null si no es un cliente (ej: email de prueba)
     * @param campaignId   campaña de promociones a la que pertenece; null si no corresponde
     */
    @Async
    public CompletableFuture<Resultado> enviar(String destinatario, String asunto, String plantilla,
                                               Map<String, Object> variables, EmailType tipo,
                                               Customer cliente, Long campaignId) {
        return CompletableFuture.completedFuture(
                enviarYRegistrar(destinatario, asunto, plantilla, variables, tipo, cliente, campaignId, null, null));
    }

    /**
     * Igual que el anterior, pero con una foto que va DENTRO del mensaje (la plantilla la muestra
     * con src="cid:imagen"). La usan las campañas para la foto de la galería: así se ve aunque el
     * sitio todavía no sea público. imagen puede ser null.
     */
    @Async
    public CompletableFuture<Resultado> enviar(String destinatario, String asunto, String plantilla,
                                               Map<String, Object> variables, EmailType tipo,
                                               Customer cliente, Long campaignId, Resource imagen) {
        return CompletableFuture.completedFuture(
                enviarYRegistrar(destinatario, asunto, plantilla, variables, tipo, cliente, campaignId, imagen, null));
    }

    /**
     * Igual que los anteriores, pero para un aviso de un pedido: queda registrado con su orderId
     * (así se puede saber si ese aviso ya se mandó). Lo usa OrderNoticeService.
     */
    @Async
    public CompletableFuture<Resultado> enviarPedido(String destinatario, String asunto, String plantilla,
                                                     Map<String, Object> variables, EmailType tipo,
                                                     Customer cliente, Long orderId) {
        return CompletableFuture.completedFuture(
                enviarYRegistrar(destinatario, asunto, plantilla, variables, tipo, cliente, null, null, orderId));
    }

    private Resultado enviarYRegistrar(String destinatario, String asunto, String plantilla, Map<String, Object> variables,
                                       EmailType tipo, Customer cliente, Long campaignId, Resource imagen, Long orderId) {
        Resultado resultado = enviarAhora(destinatario, asunto, plantilla, variables, tipo, cliente, imagen);
        registrar(destinatario, asunto, tipo, cliente, campaignId, orderId, resultado);
        return resultado;
    }

    /**
     * Arma el HTML de un email con su plantilla, sin mandarlo. Lo usan enviarAhora y la vista previa
     * de las campañas (así la vista previa es exactamente el mismo HTML que se envía).
     */
    public String armarHtml(String asunto, String plantilla, Map<String, Object> variables, String bajaUrl) {
        Context ctx = new Context();
        ctx.setVariables(variables == null ? new HashMap<>() : new HashMap<>(variables));
        ctx.setVariable("asunto", asunto);
        ctx.setVariable("negocio", businessProfile.get());   // name, whatsapp, email (panel → Ajustes)
        ctx.setVariable("publicUrl", app.getPublicUrl());
        ctx.setVariable("bajaUrl", bajaUrl);
        return templateEngine.process(plantilla, ctx);
    }

    // Hace el envío y devuelve cómo salió (sin registrar nada)
    private Resultado enviarAhora(String destinatario, String asunto, String plantilla,
                                  Map<String, Object> variables, EmailType tipo, Customer cliente, Resource imagen) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Email '{}' no enviado: no hay dirección de destino.", asunto);
            return Resultado.fallo("No hay una dirección de email de destino.");
        }

        // Promociones y cumpleaños: solo con consentimiento del cliente, y siempre con link de baja
        String bajaUrl = null;
        if (tipo != null && tipo.isPromotional()) {
            if (cliente == null || !Boolean.TRUE.equals(cliente.getMarketingConsent())) {
                log.warn("Email '{}' para {} NO enviado: el cliente no aceptó recibir promociones.", asunto, destinatario);
                return Resultado.fallo("El cliente no aceptó recibir promociones.");
            }
            bajaUrl = bajaUrl(cliente);
            if (bajaUrl == null) {
                log.warn("Email '{}' para {} NO enviado: el cliente no tiene código de baja.", asunto, destinatario);
                return Resultado.fallo("El cliente no tiene link para darse de baja.");
            }
        }

        MailAccountService.Account cuenta = mailAccounts.current().orElse(null);
        if (cuenta == null) {
            log.warn("Email '{}' para {} NO enviado: no hay una cuenta de correo configurada.", asunto, destinatario);
            return Resultado.fallo(NO_CONFIGURADO);
        }
        JavaMailSender sender = cuenta.sender();
        String username = cuenta.username();
        try {
            // 1. Se arma el HTML con la plantilla
            String html = armarHtml(asunto, plantilla, variables, bajaUrl);

            // 2. Se arma el mensaje: remitente, destinatario, asunto, HTML y el logo incrustado
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            // Gmail solo deja enviar "desde" la cuenta autenticada: el remitente es siempre el usuario SMTP,
            // con el nombre del negocio. Si hay un mail del negocio distinto, se usa para las respuestas.
            helper.setFrom(username, businessProfile.get().getName());
            String respuestas = businessProfile.get().getEmail();
            if (respuestas != null && !respuestas.isBlank()) helper.setReplyTo(respuestas);
            helper.setTo(destinatario.trim());
            helper.setSubject(asunto);
            helper.setText(html, true);
            // El logo va dentro del mensaje (cid:logo): así se ve aunque el sitio no sea público todavía
            helper.addInline("logo", new ClassPathResource("static/img/logo-email.png"));
            if (imagen != null && imagen.exists()) {
                helper.addInline("imagen", imagen);
            }
            // Encabezado estándar de baja: Gmail y otros muestran un botón "Anular suscripción"
            // que lleva a este link. Ayuda a que los mails no terminen en spam.
            if (bajaUrl != null) {
                mensaje.setHeader("List-Unsubscribe", "<" + bajaUrl + ">");
            }

            // 3. Se envía
            sender.send(mensaje);
            log.info("Email '{}' enviado a {}.", asunto, destinatario);
            return Resultado.exito();
        } catch (Exception e) {
            log.error("No se pudo enviar el email '{}' a {}: {}", asunto, destinatario, e.getMessage());
            return Resultado.fallo(explicar(e));
        }
    }

    // Guarda el intento en el historial (email_logs). Si eso falla, solo se avisa en el log:
    // un problema con el historial no tiene que impedir que el email salga.
    private void registrar(String destinatario, String asunto, EmailType tipo, Customer cliente,
                           Long campaignId, Long orderId, Resultado resultado) {
        try {
            EmailLog entrada = new EmailLog();
            entrada.setCustomer(cliente != null && cliente.getId() != null ? cliente : null);
            entrada.setRecipientEmail(destinatario);
            entrada.setType(tipo);
            entrada.setSubject(asunto);
            entrada.setStatus(resultado.ok() ? EmailStatus.SENT : EmailStatus.FAILED);
            if (!resultado.ok()) {
                String error = resultado.mensaje();
                entrada.setErrorMessage(error != null && error.length() > 500 ? error.substring(0, 500) : error);
            }
            entrada.setSentAt(LocalDateTime.now());
            entrada.setCampaignId(campaignId);
            entrada.setOrderId(orderId);
            emailLogRepository.save(entrada);
        } catch (Exception e) {
            log.error("No se pudo guardar el historial del email '{}' a {}: {}", asunto, destinatario, e.getMessage());
        }
    }

    // Traduce los errores más comunes de Gmail a algo que se entienda
    private String explicar(Exception e) {
        String texto = (e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage())).toLowerCase();
        if (texto.contains("535") || texto.contains("authentication")) {
            return "El servidor de correo rechazó el usuario o la contraseña. Con Gmail hay que usar una contraseña de aplicación.";
        }
        if (texto.contains("connect") || texto.contains("timed out") || texto.contains("unknownhost")) {
            return "No se pudo conectar con el servidor de correo. Revisá la conexión a internet y la cuenta en Ajustes → Correo de envío.";
        }
        return "No se pudo enviar el email. Revisá el registro (log) de la app para ver el detalle.";
    }
}
