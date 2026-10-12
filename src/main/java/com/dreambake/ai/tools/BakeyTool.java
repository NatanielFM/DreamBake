package com.dreambake.ai.tools;

import com.dreambake.ai.AiTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Una herramienta de Bakey: algo que el modelo puede pedir, por ejemplo "pedidos_por_fecha".
 * El modelo NO ve la base de datos: solo puede pedir estas herramientas, el servidor las ejecuta
 * (revisando permisos) y le devuelve un resultado chico en JSON.
 *
 * Hay dos tipos (kind):
 *  - CONSULTA (paso 5.3): solo leen datos; el handler devuelve el resultado.
 *  - ACCION (paso 5.4): cambian datos. Su "proposer" NO cambia nada: revisa los datos y arma una
 *    propuesta (ActionProposal) que queda esperando que la persona toque "Confirmar".
 *
 * @param name        nombre que usa el modelo (sin espacios)
 * @param description para qué sirve, en español: el modelo la lee para decidir cuándo usarla
 * @param parameters  qué datos recibe, en formato JSON Schema (ver params/param)
 * @param roles       qué roles la pueden usar ("ADMIN", "PASTELERO")
 * @param kind        CONSULTA o ACCION
 * @param handler     (consultas) el código que la ejecuta y devuelve el resultado
 * @param proposer    (acciones) el código que revisa los datos y arma la propuesta, sin ejecutarla
 */
public record BakeyTool(String name, String description, Map<String, Object> parameters, Set<String> roles,
                        Kind kind,
                        BiFunction<ToolContext, ToolArgs, Map<String, Object>> handler,
                        BiFunction<ToolContext, ToolArgs, ActionProposal> proposer) {

    public enum Kind { CONSULTA, ACCION }

    public static final Set<String> ALL_STAFF = Set.of("ADMIN", "PASTELERO");
    public static final Set<String> ADMIN_ONLY = Set.of("ADMIN");

    /** Herramienta de consulta (solo lee). */
    public BakeyTool(String name, String description, Map<String, Object> parameters, Set<String> roles,
                     BiFunction<ToolContext, ToolArgs, Map<String, Object>> handler) {
        this(name, description, parameters, roles, Kind.CONSULTA, handler, null);
    }

    /** Herramienta de acción (cambia datos, siempre con confirmación de la persona). */
    public static BakeyTool action(String name, String description, Map<String, Object> parameters, Set<String> roles,
                                   BiFunction<ToolContext, ToolArgs, ActionProposal> proposer) {
        return new BakeyTool(name, description, parameters, roles, Kind.ACCION, null, proposer);
    }

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

    /** Un parámetro: nombre, esquema JSON (tipo y descripción) y si es obligatorio. */
    public record Param(String name, Map<String, Object> schema, boolean required) {}

    /** Parámetro obligatorio de tipo simple ("string", "integer", "number", "boolean"). */
    public static Param param(String name, String type, String description) {
        return new Param(name, Map.of("type", type, "description", description), true);
    }

    public static Param optional(String name, String type, String description) {
        return new Param(name, Map.of("type", type, "description", description), false);
    }

    /**
     * Parámetro que es una LISTA de objetos (por ejemplo los productos de un pedido).
     * @param fields propiedades de cada objeto: nombre → param(…)/optional(…)
     */
    public static Param list(String name, String description, Param... fields) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", params(fields));
        return new Param(name, schema, true);
    }

    /** Arma el JSON Schema de los parámetros: {"type":"object","properties":{…},"required":[…]}. */
    public static Map<String, Object> params(Param... params) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Param p : params) {
            properties.put(p.name(), p.schema());
            if (p.required()) required.add(p.name());
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }
}
