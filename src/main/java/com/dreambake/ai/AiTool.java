package com.dreambake.ai;

import java.util.Map;

/**
 * Una herramienta que el modelo puede pedir usar (por ejemplo "buscar pedidos de mañana").
 *
 * @param name        nombre corto, sin espacios (ej: "pedidos_por_fecha")
 * @param description para qué sirve, en palabras simples: el modelo la lee para decidir cuándo usarla
 * @param parameters  qué datos necesita, en formato "JSON Schema" (tipo object con sus propiedades)
 */
public record AiTool(String name, String description, Map<String, Object> parameters) {
}
