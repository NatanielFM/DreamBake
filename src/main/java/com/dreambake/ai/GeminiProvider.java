package com.dreambake.ai;

import com.dreambake.config.BakeyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementación de AiProvider para Gemini, la IA de Google (paso 5.1).
 *
 * Habla directo con la API REST de Gemini usando RestClient de Spring (sin el SDK de Google):
 *
 *   POST {base-url}/models/{modelo}:generateContent
 *   encabezado x-goog-api-key: la clave
 *   cuerpo: systemInstruction (quién es Bakey), contents (la conversación) y tools (herramientas)
 *
 * Formato verificado en la documentación oficial (ai.google.dev, octubre 2026).
 *
 * Seguridad de la clave: va en un encabezado y NO en la dirección (?key=…), así no aparece en los
 * logs ni en los mensajes de error. Nunca se manda al navegador ni se escribe en el log.
 */
@Component
public class GeminiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    // Motivos de corte que significan "Gemini no quiso responder por sus filtros"
    private static final Set<String> BLOCKED_REASONS = Set.of(
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION");

    private final BakeyProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public GeminiProvider(BakeyProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;

        // Tiempos máximos: 10 s para conectarse y "timeout-seconds" (30) para esperar la respuesta.
        // Si Gemini tarda más, se corta y Bakey contesta un mensaje amable en vez de quedarse colgado.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getGemini().getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public AiReply chat(String systemPrompt, List<AiMessage> history, List<AiTool> tools) {
        if (!isConfigured()) {
            throw new AiException(AiException.Kind.NOT_CONFIGURED, "Falta bakey.gemini.api-key");
        }
        String apiKey = properties.getGemini().getApiKey();
        String model = properties.getGemini().getModel();

        String requestJson = jsonMapper.writeValueAsString(buildRequest(systemPrompt, history, tools));
        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri(properties.getGemini().getBaseUrl() + "/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    // Si Gemini responde con error (4xx/5xx), se traduce a un AiException con su tipo
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw errorFor(response, apiKey, model);
                    })
                    .body(String.class);
        } catch (AiException e) {
            throw e;
        } catch (RestClientException e) {
            // Problemas de red: sin internet, no se pudo conectar o se pasó el tiempo de espera
            if (isTimeout(e)) {
                log.warn("Bakey: Gemini no respondió en {} segundos", properties.getGemini().getTimeoutSeconds());
                throw new AiException(AiException.Kind.TIMEOUT, "Tiempo de espera agotado", e);
            }
            log.warn("Bakey: no se pudo conectar con Gemini ({})", e.getClass().getSimpleName());
            throw new AiException(AiException.Kind.FAILED, "Error de conexión con Gemini", e);
        }
        return parseResponse(responseJson);
    }

    // ---------------------------------------------------------------------
    // Armado del pedido
    // ---------------------------------------------------------------------

    private Map<String, Object> buildRequest(String systemPrompt, List<AiMessage> history, List<AiTool> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", toContents(history));
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> declarations = new ArrayList<>();
            for (AiTool tool : tools) {
                Map<String, Object> declaration = new LinkedHashMap<>();
                declaration.put("name", tool.name());
                declaration.put("description", tool.description());
                if (tool.parameters() != null && !tool.parameters().isEmpty()) {
                    declaration.put("parameters", tool.parameters());
                }
                declarations.add(declaration);
            }
            body.put("tools", List.of(Map.of("functionDeclarations", declarations)));
        }
        return body;
    }

    /*
     * Traduce la conversación al formato de Gemini. Gemini solo tiene dos roles:
     *  - "user":  los mensajes de la persona y también los resultados de las herramientas (functionResponse)
     *  - "model": las respuestas del modelo (texto y/o functionCall, con su thoughtSignature tal cual vino)
     * Varios resultados de herramientas seguidos van juntos en un solo mensaje (así lo pide Gemini
     * cuando el modelo pidió varias herramientas a la vez).
     */
    private List<Map<String, Object>> toContents(List<AiMessage> history) {
        List<Map<String, Object>> contents = new ArrayList<>();
        List<Map<String, Object>> pendingResults = new ArrayList<>();

        for (AiMessage message : history) {
            if (message.role() == AiMessage.Role.TOOL) {
                pendingResults.add(functionResponsePart(message.toolResult()));
                continue;
            }
            if (!pendingResults.isEmpty()) {
                contents.add(content("user", pendingResults));
                pendingResults = new ArrayList<>();
            }
            List<Map<String, Object>> parts = new ArrayList<>();
            if (message.text() != null && !message.text().isBlank()) {
                parts.add(Map.of("text", message.text()));
            }
            if (message.role() == AiMessage.Role.MODEL) {
                for (AiToolCall call : message.toolCalls()) {
                    parts.add(functionCallPart(call));
                }
            }
            if (!parts.isEmpty()) {
                contents.add(content(message.role() == AiMessage.Role.MODEL ? "model" : "user", parts));
            }
        }
        if (!pendingResults.isEmpty()) {
            contents.add(content("user", pendingResults));
        }
        return contents;
    }

    private Map<String, Object> content(String role, List<Map<String, Object>> parts) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", role);
        content.put("parts", parts);
        return content;
    }

    private Map<String, Object> functionCallPart(AiToolCall call) {
        Map<String, Object> functionCall = new LinkedHashMap<>();
        if (call.id() != null) functionCall.put("id", call.id());
        functionCall.put("name", call.name());
        functionCall.put("args", call.args() == null ? Map.of() : call.args());
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("functionCall", functionCall);
        // La firma se devuelve exactamente como la mandó Gemini (si falta, Gemini 3 da error 400)
        if (call.signature() != null) part.put("thoughtSignature", call.signature());
        return part;
    }

    private Map<String, Object> functionResponsePart(AiToolResult result) {
        Map<String, Object> functionResponse = new LinkedHashMap<>();
        if (result.callId() != null) functionResponse.put("id", result.callId());
        functionResponse.put("name", result.name());
        functionResponse.put("response", result.result() == null ? Map.of() : result.result());
        return Map.of("functionResponse", functionResponse);
    }

    // ---------------------------------------------------------------------
    // Lectura de la respuesta
    // ---------------------------------------------------------------------

    /*
     * La respuesta trae candidates[0].content.parts: cada parte es un texto, un pedido de herramienta
     * (functionCall) o un "pensamiento" interno del modelo (thought: true), que no se muestra.
     */
    @SuppressWarnings("unchecked")
    private AiReply parseResponse(String responseJson) {
        Map<String, Object> response;
        try {
            response = jsonMapper.readValue(responseJson, Map.class);
        } catch (JacksonException e) {
            log.warn("Bakey: Gemini mandó una respuesta que no se pudo leer");
            throw new AiException(AiException.Kind.FAILED, "Respuesta ilegible", e);
        }

        List<Object> candidates = response.get("candidates") instanceof List<?> list ? (List<Object>) list : List.of();
        if (candidates.isEmpty()) {
            // Sin respuesta: casi siempre es porque Gemini bloqueó la pregunta (promptFeedback.blockReason)
            Object feedback = response.get("promptFeedback");
            if (feedback instanceof Map<?, ?> map && map.get("blockReason") != null) {
                log.info("Bakey: Gemini bloqueó la pregunta ({})", map.get("blockReason"));
                throw new AiException(AiException.Kind.BLOCKED, "Pregunta bloqueada: " + map.get("blockReason"));
            }
            throw new AiException(AiException.Kind.FAILED, "Respuesta sin candidatos");
        }

        Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
        String finishReason = String.valueOf(candidate.get("finishReason"));
        StringBuilder text = new StringBuilder();
        List<AiToolCall> toolCalls = new ArrayList<>();

        if (candidate.get("content") instanceof Map<?, ?> content && content.get("parts") instanceof List<?> parts) {
            for (Object item : parts) {
                if (!(item instanceof Map<?, ?> part)) continue;
                if (Boolean.TRUE.equals(part.get("thought"))) continue; // razonamiento interno: no se muestra
                if (part.get("text") instanceof String partText) {
                    text.append(partText);
                }
                if (part.get("functionCall") instanceof Map<?, ?> call) {
                    Map<String, Object> args = call.get("args") instanceof Map<?, ?> a
                            ? new LinkedHashMap<>((Map<String, Object>) a) : new LinkedHashMap<>();
                    toolCalls.add(new AiToolCall(
                            call.get("id") == null ? null : String.valueOf(call.get("id")),
                            String.valueOf(call.get("name")),
                            args,
                            part.get("thoughtSignature") == null ? null : String.valueOf(part.get("thoughtSignature"))));
                }
            }
        }

        if (text.toString().isBlank() && toolCalls.isEmpty()) {
            if (BLOCKED_REASONS.contains(finishReason)) {
                log.info("Bakey: Gemini cortó la respuesta ({})", finishReason);
                throw new AiException(AiException.Kind.BLOCKED, "Respuesta bloqueada: " + finishReason);
            }
            log.warn("Bakey: Gemini respondió vacío (finishReason={})", finishReason);
            throw new AiException(AiException.Kind.FAILED, "Respuesta vacía: " + finishReason);
        }
        return new AiReply(text.toString().trim(), toolCalls);
    }

    // ---------------------------------------------------------------------
    // Errores
    // ---------------------------------------------------------------------

    /*
     * Traduce el código de error de Gemini:
     *   429 → se agotó la cuota (pedidos por minuto o por día del plan gratuito)
     *   400/401/403 → clave inválida o pedido mal armado; 404 → el modelo no existe; 5xx → falla de Google
     * El detalle va al log (sin la clave) para que el administrador pueda revisar qué pasó.
     */
    private AiException errorFor(ClientHttpResponse response, String apiKey, String model) throws IOException {
        int status = response.getStatusCode().value();
        String detail = safeDetail(response, apiKey);
        if (status == 429) {
            log.warn("Bakey: Gemini respondió 429 (cuota agotada): {}", detail);
            return new AiException(AiException.Kind.QUOTA, "Cuota de Gemini agotada");
        }
        if (status == 503) {
            // Google sobrecargado ("high demand"): para la persona es lo mismo que la cuota, se reintenta en un rato
            log.warn("Bakey: Gemini respondió 503 (sobrecargado): {}", detail);
            return new AiException(AiException.Kind.QUOTA, "Gemini sobrecargado");
        }
        if (status == 404) {
            log.warn("Bakey: Gemini no encontró el modelo \"{}\" (revisá bakey.gemini.model): {}", model, detail);
        } else if (status == 400 || status == 401 || status == 403) {
            log.warn("Bakey: Gemini rechazó el pedido ({}). ¿La clave bakey.gemini.api-key es válida? Detalle: {}",
                    status, detail);
        } else {
            log.warn("Bakey: Gemini respondió error {}: {}", status, detail);
        }
        return new AiException(AiException.Kind.FAILED, "Gemini respondió " + status);
    }

    // Lee el mensaje de error de Gemini ({"error": {"message": …}}), lo acorta y le borra la clave por las dudas
    @SuppressWarnings("unchecked")
    private String safeDetail(ClientHttpResponse response, String apiKey) {
        String raw;
        try {
            raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(sin detalle)";
        }
        String detail = raw;
        try {
            Map<String, Object> json = jsonMapper.readValue(raw, Map.class);
            if (json.get("error") instanceof Map<?, ?> error && error.get("message") != null) {
                detail = String.valueOf(error.get("message"));
            }
        } catch (JacksonException ignored) {
            // No era JSON: se usa el texto tal cual
        }
        if (!apiKey.isEmpty()) detail = detail.replace(apiKey, "[clave oculta]");
        detail = detail.replaceAll("\\s+", " ").trim();
        // 600 letras: en el 429 Google dice al final qué cuota se agotó (por minuto o por día)
        return detail.length() > 600 ? detail.substring(0, 600) + "…" : detail;
    }

    // ¿El error de red fue por pasarse del tiempo de espera?
    private boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
