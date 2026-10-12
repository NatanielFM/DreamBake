package com.dreambake.service;

import com.dreambake.ai.AiException;
import com.dreambake.ai.AiMessage;
import com.dreambake.ai.AiProvider;
import com.dreambake.ai.AiReply;
import com.dreambake.config.AppProperties;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lógica de Bakey, el asistente virtual del panel (paso 5.1: conversación sin herramientas).
 *
 * Recibe el mensaje de la persona, revisa que Bakey esté configurado y que la persona no haya
 * pasado su límite, le manda la conversación al proveedor de IA (AiProvider: hoy Gemini) y
 * devuelve la respuesta. Si algo falla, devuelve un mensaje amable: nunca errores técnicos.
 *
 * La conversación se guarda en la sesión del usuario (no en el navegador): así, cuando Bakey use
 * herramientas (pasos 5.3 y 5.4), nadie puede inventar desde el navegador un "resultado de
 * herramienta" falso. Se borra al cerrar sesión o con "Nueva conversación" (reset).
 */
@Service
public class BakeyService {

    private static final Logger log = LoggerFactory.getLogger(BakeyService.class);

    static final String SESSION_HISTORY = "bakey.history";
    public static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_HISTORY = 20;   // últimos mensajes que se le mandan al modelo

    // Mensajes que ve la persona (español de Argentina, sin palabras técnicas)
    static final String MSG_NOT_CONFIGURED = "Bakey todavía no está configurado. Pedile al administrador que cargue la clave de Gemini.";
    static final String MSG_EMPTY = "Escribime tu pregunta y te ayudo.";
    static final String MSG_TOO_LONG = "Tu mensaje es muy largo. Probá escribirlo más corto (hasta " + MAX_MESSAGE_LENGTH + " letras).";
    static final String MSG_MINUTE_LIMIT = "Me hiciste muchas preguntas seguidas. Esperá un minuto y seguimos.";
    static final String MSG_DAY_LIMIT = "Por hoy llegamos al máximo de preguntas. Mañana seguimos.";
    static final String MSG_QUOTA = "Estoy con mucha demanda, probá en un minuto.";
    static final String MSG_TIMEOUT = "Tardé demasiado en pensar la respuesta. Probá de nuevo en un ratito.";
    static final String MSG_BLOCKED = "Prefiero no responder eso. ¿Te ayudo con otra cosa de la pastelería?";
    static final String MSG_ERROR = "Uy, no pude responder ahora. Probá de nuevo en un momento.";

    /** Cómo terminó el mensaje (el controlador lo usa para elegir el código HTTP). */
    public enum Status { OK, EMPTY, TOO_LONG, NOT_CONFIGURED, LIMIT, BUSY, TIMEOUT, BLOCKED, ERROR }

    /** Respuesta para el navegador: el estado y el texto que se le muestra a la persona. */
    public record BakeyAnswer(Status status, String reply) {}

    private final AiProvider provider;
    private final BakeyRateLimiter rateLimiter;
    private final AppProperties appProperties;

    public BakeyService(AiProvider provider, BakeyRateLimiter rateLimiter, AppProperties appProperties) {
        this.provider = provider;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
    }

    /** true si Bakey puede funcionar (hay clave de Gemini cargada). */
    public boolean isConfigured() {
        return provider.isConfigured();
    }

    /** Procesa un mensaje de la persona logueada (email) y devuelve la respuesta de Bakey. */
    public BakeyAnswer chat(String email, HttpSession session, String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) return new BakeyAnswer(Status.EMPTY, MSG_EMPTY);
        if (text.length() > MAX_MESSAGE_LENGTH) return new BakeyAnswer(Status.TOO_LONG, MSG_TOO_LONG);
        if (!provider.isConfigured()) return new BakeyAnswer(Status.NOT_CONFIGURED, MSG_NOT_CONFIGURED);

        switch (rateLimiter.tryAcquire(email)) {
            case MINUTE_LIMIT: return new BakeyAnswer(Status.LIMIT, MSG_MINUTE_LIMIT);
            case DAY_LIMIT:    return new BakeyAnswer(Status.LIMIT, MSG_DAY_LIMIT);
            default:           break;
        }

        // Conversación hasta ahora + el mensaje nuevo
        List<AiMessage> history = new ArrayList<>(getHistory(session));
        history.add(AiMessage.user(text));

        AiReply reply;
        try {
            reply = provider.chat(systemPrompt(), trim(history), List.of()); // paso 5.1: sin herramientas
        } catch (AiException e) {
            // Si falló, la pregunta NO se guarda en la conversación (la persona la vuelve a mandar)
            return switch (e.getKind()) {
                case QUOTA -> new BakeyAnswer(Status.BUSY, MSG_QUOTA);
                case TIMEOUT -> new BakeyAnswer(Status.TIMEOUT, MSG_TIMEOUT);
                case BLOCKED -> new BakeyAnswer(Status.BLOCKED, MSG_BLOCKED);
                case NOT_CONFIGURED -> new BakeyAnswer(Status.NOT_CONFIGURED, MSG_NOT_CONFIGURED);
                case FAILED -> new BakeyAnswer(Status.ERROR, MSG_ERROR);
            };
        } catch (RuntimeException e) {
            // Cualquier otra cosa inesperada: se anota en el log y la persona ve el mensaje amable
            log.error("Bakey: error inesperado al responder", e);
            return new BakeyAnswer(Status.ERROR, MSG_ERROR);
        }

        // Sin herramientas todavía, el modelo no debería pedir ninguna; si igual lo hace, se responde amable
        if (reply.text().isBlank()) {
            log.warn("Bakey: el modelo pidió herramientas sin tener ninguna disponible");
            return new BakeyAnswer(Status.ERROR, MSG_ERROR);
        }

        history.add(new AiMessage(AiMessage.Role.MODEL, reply.text(), List.of(), null));
        saveHistory(session, trim(history));
        return new BakeyAnswer(Status.OK, reply.text());
    }

    /** Borra la conversación guardada ("Nueva conversación"). */
    public void reset(HttpSession session) {
        session.removeAttribute(SESSION_HISTORY);
    }

    // ---------------------------------------------------------------------

    /*
     * Instrucciones fijas para el modelo: quién es Bakey y cómo tiene que contestar.
     * Se incluye la fecha de hoy para que pueda entender "mañana", "el sábado", etc.
     */
    private String systemPrompt() {
        String today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"))
                .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-AR")));
        return """
                Sos Bakey, el asistente virtual del sistema DreamBake de la pastelería "%s" (Posadas, Misiones).
                Ayudás a la dueña y a los pasteleros con dudas del día a día de la pastelería y del sistema.
                Reglas:
                - Respondé siempre en español de Argentina (voseo), con calidez y en pocas oraciones.
                - Usá palabras simples: quien te escribe no es técnico.
                - Todavía no tenés acceso a los datos del sistema (pedidos, stock, clientes ni recetas).
                  Si te preguntan por esos datos, decí con amabilidad que todavía no podés consultarlos.
                  Nunca inventes datos, números ni nombres.
                - Si no sabés algo, decilo.
                Hoy es %s.
                """.formatted(appProperties.getBusiness().getName(), today);
    }

    @SuppressWarnings("unchecked")
    private List<AiMessage> getHistory(HttpSession session) {
        Object saved = session.getAttribute(SESSION_HISTORY);
        return saved instanceof List<?> list ? (List<AiMessage>) list : List.of();
    }

    private void saveHistory(HttpSession session, List<AiMessage> history) {
        // Mutex de la sesión: si llegan dos mensajes a la vez, no se pisan al guardar
        synchronized (WebUtils.getSessionMutex(session)) {
            session.setAttribute(SESSION_HISTORY, new ArrayList<>(history));
        }
    }

    /*
     * Deja solo los últimos mensajes (para no mandar conversaciones enormes y gastar cuota).
     * La conversación recortada siempre empieza con un mensaje de la persona: así nunca queda
     * un resultado de herramienta separado del pedido que lo originó.
     */
    private List<AiMessage> trim(List<AiMessage> history) {
        int start = Math.max(0, history.size() - MAX_HISTORY);
        while (start < history.size() && history.get(start).role() != AiMessage.Role.USER) start++;
        return new ArrayList<>(history.subList(start, history.size()));
    }
}
