package com.dreambake.ai.tools;

import com.dreambake.ai.AiTool;
import com.dreambake.ai.AiToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de herramientas de Bakey (paso 5.3): sabe qué herramientas hay, cuáles puede usar cada rol
 * y las ejecuta.
 *
 * Los permisos se controlan DOS veces:
 *  1. toolsFor: al modelo solo se le ofrecen las herramientas que el rol puede usar (al pastelero ni
 *     siquiera se le cuenta que existe "resumen_ventas").
 *  2. execute: antes de ejecutar se vuelve a revisar, por si el modelo pide una que no se le ofreció.
 * Así un pastelero no puede ver ventas ni clientes aunque se lo pida a Bakey de mil maneras.
 */
@Component
public class BakeyToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(BakeyToolRegistry.class);

    private final Map<String, BakeyTool> tools = new LinkedHashMap<>();

    public BakeyToolRegistry(BakeyQueryTools queryTools) {
        for (BakeyTool tool : queryTools.tools()) {
            tools.put(tool.name(), tool);
        }
    }

    /** Declaraciones de las herramientas que esta persona puede usar (lo que se le manda al modelo). */
    public List<AiTool> toolsFor(ToolContext ctx) {
        return tools.values().stream().filter(t -> t.allowedFor(ctx)).map(BakeyTool::declaration).toList();
    }

    /**
     * Ejecuta lo que pidió el modelo y devuelve el resultado. Nunca tira excepción: si algo sale mal,
     * devuelve {"error": "…"} en español para que el modelo se lo explique a la persona.
     * readOnly: la transacción es de solo lectura (las herramientas solo consultan) y deja leer las
     * recetas de cada producto, que la base carga recién cuando se usan.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> execute(AiToolCall call, ToolContext ctx) {
        BakeyTool tool = tools.get(call.name());
        if (tool == null) {
            return Map.of("error", "No existe la herramienta " + call.name() + ".");
        }
        if (!tool.allowedFor(ctx)) {
            log.warn("Bakey: {} pidió la herramienta {} sin permiso", ctx.user().getEmail(), call.name());
            return Map.of("error", "Esta persona no tiene permiso para ver esa información.");
        }
        try {
            log.info("Bakey: {} usa {} {}", ctx.user().getEmail(), call.name(), call.args());
            return tool.handler().apply(ctx, new ToolArgs(call.args()));
        } catch (ToolArgs.InvalidArgException e) {
            return Map.of("error", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Bakey: falló la herramienta {}", call.name(), e);
            return Map.of("error", "No se pudo consultar esa información en este momento.");
        }
    }
}
