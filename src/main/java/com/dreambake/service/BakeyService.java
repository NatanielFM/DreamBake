package com.dreambake.service;

import com.dreambake.ai.AiException;
import com.dreambake.ai.AiMessage;
import com.dreambake.ai.AiProvider;
import com.dreambake.ai.AiReply;
import com.dreambake.ai.AiTool;
import com.dreambake.ai.AiToolCall;
import com.dreambake.ai.AiToolResult;
import com.dreambake.ai.tools.BakeyToolRegistry;
import com.dreambake.ai.tools.ToolContext;
import com.dreambake.config.AppProperties;
import com.dreambake.entity.User;
import com.dreambake.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lógica de Bakey, el asistente virtual del panel (paso 5.1; desde el 5.3 con herramientas de consulta).
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

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY_FULL = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", AR);
    private static final DateTimeFormatter DAY_LIST = DateTimeFormatter.ofPattern("EEEE dd/MM", AR);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

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

    /** Respuesta para el navegador: el estado, el texto para la persona y qué herramientas usó Bakey. */
    public record BakeyAnswer(Status status, String reply, List<String> tools) {
        public BakeyAnswer(Status status, String reply) {
            this(status, reply, List.of());
        }
    }

    // Máximo de vueltas con el modelo por mensaje (cada vuelta es un pedido a Gemini)
    static final int MAX_ROUNDS = 5;

    private final AiProvider provider;
    private final BakeyRateLimiter rateLimiter;
    private final AppProperties appProperties;
    private final BakeyToolRegistry toolRegistry;
    private final UserRepository userRepository;

    public BakeyService(AiProvider provider, BakeyRateLimiter rateLimiter, AppProperties appProperties,
                        BakeyToolRegistry toolRegistry, UserRepository userRepository) {
        this.provider = provider;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
        this.toolRegistry = toolRegistry;
        this.userRepository = userRepository;
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

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return new BakeyAnswer(Status.ERROR, MSG_ERROR);
        boolean admin = user.getRoles() != null && user.getRoles().contains("ADMIN");
        ToolContext ctx = new ToolContext(user, admin, text, LocalDate.now(ZONE));
        List<AiTool> tools = toolRegistry.toolsFor(ctx);   // solo las que este rol puede usar

        // Conversación de trabajo: lo anterior + el mensaje nuevo. Durante este mensaje se le van
        // agregando los pedidos de herramientas del modelo y sus resultados.
        List<AiMessage> history = new ArrayList<>(getHistory(session));
        List<AiMessage> working = trim(history);
        working.add(AiMessage.user(text));
        List<String> usedTools = new ArrayList<>();

        String finalText = null;
        try {
            /*
             * EL BUCLE DE HERRAMIENTAS (function calling):
             *  1. Se le manda al modelo la conversación y la lista de herramientas.
             *  2. Si responde con texto, esa es la respuesta: listo.
             *  3. Si pide herramientas, el servidor las ejecuta (revisando permisos en BakeyToolRegistry),
             *     le devuelve los resultados y vuelve al paso 1.
             * Como mucho MAX_ROUNDS vueltas. En la última se lo llama SIN herramientas, para que
             * responda con lo que ya consultó (así nunca queda dando vueltas para siempre).
             */
            for (int round = 1; round <= MAX_ROUNDS; round++) {
                boolean lastRound = round == MAX_ROUNDS;
                AiReply reply = provider.chat(systemPrompt(ctx), working, lastRound ? List.of() : tools);

                if (!reply.hasToolCalls()) {
                    finalText = reply.text();
                    break;
                }
                // El pedido del modelo se guarda tal cual (con su firma interna) y después cada resultado
                working.add(AiMessage.model(reply));
                for (AiToolCall call : reply.toolCalls()) {
                    usedTools.add(call.name());
                    Map<String, Object> result = toolRegistry.execute(call, ctx);
                    working.add(AiMessage.tool(new AiToolResult(call.id(), call.name(), result)));
                }
            }
        } catch (AiException e) {
            // Si falló, la pregunta NO se guarda en la conversación (la persona la vuelve a mandar)
            return switch (e.getKind()) {
                case QUOTA -> new BakeyAnswer(Status.BUSY, MSG_QUOTA, usedTools);
                case TIMEOUT -> new BakeyAnswer(Status.TIMEOUT, MSG_TIMEOUT, usedTools);
                case BLOCKED -> new BakeyAnswer(Status.BLOCKED, MSG_BLOCKED, usedTools);
                case NOT_CONFIGURED -> new BakeyAnswer(Status.NOT_CONFIGURED, MSG_NOT_CONFIGURED, usedTools);
                case FAILED -> new BakeyAnswer(Status.ERROR, MSG_ERROR, usedTools);
            };
        } catch (RuntimeException e) {
            // Cualquier otra cosa inesperada: se anota en el log y la persona ve el mensaje amable
            log.error("Bakey: error inesperado al responder", e);
            return new BakeyAnswer(Status.ERROR, MSG_ERROR, usedTools);
        }

        if (finalText == null || finalText.isBlank()) {
            log.warn("Bakey: el modelo no dio una respuesta de texto (herramientas usadas: {})", usedTools);
            return new BakeyAnswer(Status.ERROR, MSG_ERROR, usedTools);
        }

        /*
         * En la sesión se guarda SOLO la pregunta y la respuesta final. Los resultados de las herramientas
         * (listas de pedidos, clientes…) se descartan al terminar: se guardan menos datos personales y
         * las próximas preguntas mandan menos texto a Gemini. Si hace falta, Bakey vuelve a consultar.
         */
        history.add(AiMessage.user(text));
        history.add(new AiMessage(AiMessage.Role.MODEL, finalText, List.of(), null));
        saveHistory(session, trim(history));
        return new BakeyAnswer(Status.OK, finalText, usedTools);
    }

    /** Borra la conversación guardada ("Nueva conversación"). */
    public void reset(HttpSession session) {
        session.removeAttribute(SESSION_HISTORY);
    }

    // ---------------------------------------------------------------------

    /*
     * Instrucciones para el modelo (se arman en cada mensaje): quién es Bakey, cómo contesta y qué día es.
     * Se le pasan la fecha y la hora de Argentina y los próximos 7 días con su nombre, para que entienda
     * "el viernes", "mañana" o "el finde" sin equivocarse de fecha.
     */
    private String systemPrompt(ToolContext ctx) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        StringBuilder week = new StringBuilder();
        for (int i = 0; i <= 7; i++) {
            LocalDate d = ctx.today().plusDays(i);
            week.append(i == 0 ? "hoy " : "").append(d.format(DAY_LIST)).append(" = ").append(d).append(i < 7 ? "; " : "");
        }
        String role = ctx.admin()
                ? "la administradora (dueña) del negocio"
                : "un/a pastelero/a del equipo. No ve los montos de los pedidos, cobros, señas ni ganancias: no hables de eso. "
                        + "Los precios de la lista de productos de la tienda sí se los podés decir (son públicos)";
        return """
                Sos Bakey, el asistente de la pastelería "%s" (Posadas, Misiones), dentro del sistema DreamBake.
                Ayudás al equipo con el día a día: pedidos y entregas, stock e insumos, recetas, clientes y recordatorios.
                Quien te escribe es %s.

                Reglas:
                - Respondé en español rioplatense (voseo), breve y claro, para personas que no son técnicas.
                  Usá listas cortas cuando haya varios elementos. Nada de palabras técnicas ni de JSON.
                - Los datos del negocio SOLO los sacás de las herramientas. Si una herramienta no te da el dato,
                  decí que no lo sabés. Nunca inventes pedidos, nombres, cantidades, fechas ni precios.
                - Si una herramienta devuelve "error", explicalo con palabras simples.
                - Fechas: hoy es %s (hora de Argentina %s). Próximos días: %s.
                  A las herramientas pasales las fechas como AAAA-MM-DD. "El finde" = sábado y domingo próximos.
                - Datos de contacto (teléfono, email, dirección): no los pidas ni los muestres salvo que la persona los
                  pida explícitamente; solo en ese caso usá incluir_contacto = true.
                - Si te preguntan algo que no tiene que ver con la pastelería o el sistema, decí amablemente que solo
                  podés ayudar con temas del negocio.
                """.formatted(appProperties.getBusiness().getName(), role,
                ctx.today().format(DAY_FULL), now.format(HOUR), week);
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
