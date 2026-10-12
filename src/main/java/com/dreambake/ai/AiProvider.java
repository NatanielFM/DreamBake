package com.dreambake.ai;

import java.util.List;

/**
 * Proveedor de inteligencia artificial que usa Bakey (patrón de diseño Strategy).
 *
 * El resto del sistema (BakeyService, el controlador, las herramientas de los pasos 5.3 y 5.4)
 * solo conoce ESTA interfaz, nunca a Gemini directamente. Hoy la única implementación es
 * GeminiProvider; si mañana conviene usar otro proveedor (OpenAI, Claude, un modelo local…),
 * alcanza con escribir otra clase que implemente esta interfaz y marcarla como la activa:
 * no hay que tocar la lógica de Bakey, ni el controlador, ni las herramientas.
 *
 * Cada proveedor traduce estos datos "neutros" (AiMessage, AiTool, AiReply) a su propio formato.
 */
public interface AiProvider {

    /** true si tiene lo necesario para funcionar (por ejemplo, la clave de la API). */
    boolean isConfigured();

    /**
     * Manda la conversación al modelo y devuelve su respuesta.
     *
     * @param systemPrompt instrucciones fijas (quién es Bakey, cómo tiene que contestar)
     * @param history      conversación hasta ahora, terminando con el último mensaje del usuario
     * @param tools        herramientas que el modelo puede pedir usar (lista vacía = ninguna)
     * @return texto para mostrar o pedidos de llamada a herramientas (ver AiReply)
     * @throws AiException si no se pudo obtener respuesta (cuota agotada, demora, error…)
     */
    AiReply chat(String systemPrompt, List<AiMessage> history, List<AiTool> tools);
}
