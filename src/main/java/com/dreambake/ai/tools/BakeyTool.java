package com.dreambake.ai.tools;

import com.dreambake.ai.AiTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Una herramienta de Bakey (paso 5.3): una consulta que el modelo puede pedir, por ejemplo
 * "pedidos_por_fecha". El modelo NO ve la base de datos: solo puede pedir estas herramientas, el
 * servidor las ejecuta (revisando permisos) y le devuelve un resultado chico en JSON.
 *
 * @param name        nombre que usa el modelo (sin espacios)
 * @param description para qué sirve, en español: el modelo la lee para decidir cuándo usarla
 * @param parameters  qué datos recibe, en formato JSON Schema (ver params/param)
 * @param roles       qué roles la pueden usar ("ADMIN", "PASTELERO")
 * @param handler     el código Java que la ejecuta y devuelve el resultado
 */
public record BakeyTool(String name, String description, Map<String, Object> parameters, Set<String> roles,
                        BiFunction<ToolContext, ToolArgs, Map<String, Object>> handler) {

    public static final Set<String> ALL_STAFF = Set.of("ADMIN", "PASTELERO");
    public static final Set<String> ADMIN_ONLY = Set.of("ADMIN");

    /** La declaración que se le manda al modelo (sin el código). */
    public AiTool declaration() {
        return new AiTool(name, description, parameters);
    }

    public boolean allowedFor(ToolContext ctx) {
        return roles.contains(ctx.admin() ? "ADMIN" : "PASTELERO");
    }

    // ---------------------------------------------------------------------
    // Ayudas para escribir los parámetros sin tanto código repetido
    // ---------------------------------------------------------------------

    /** Un parámetro: nombre, tipo ("string", "integer", "boolean") y descripción. */
    public record Param(String name, String type, String description, boolean required) {}

    public static Param param(String name, String type, String description) {
        return new Param(name, type, description, true);
    }

    public static Param optional(String name, String type, String description) {
        return new Param(name, type, description, false);
    }

    /** Arma el JSON Schema de los parámetros: {"type":"object","properties":{…},"required":[…]}. */
    public static Map<String, Object> params(Param... params) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Param p : params) {
            properties.put(p.name(), Map.of("type", p.type(), "description", p.description()));
            if (p.required()) required.add(p.name());
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }
}
