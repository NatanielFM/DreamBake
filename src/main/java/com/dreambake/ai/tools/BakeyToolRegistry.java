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
 * Registro de herramientas de Bakey (pasos 5.3 y 5.4): sabe qué herramientas hay, cuáles puede usar
 * cada rol y las ejecuta.
 *
 * Los permisos se controlan DOS veces:
 *  1. toolsFor: al modelo solo se le ofrecen las herramientas que el rol puede usar (al pastelero ni
 *     siquiera se le cuenta que existe "resumen_ventas" o "ajustar_stock").
 *  2. execute: antes de ejecutar se vuelve a revisar, por si el modelo pide una que no se le ofreció.
 *
 * Las herramientas de ACCIÓN no se ejecutan acá: se revisan sus datos y quedan como acción pendiente
 * (BakeyActionService) hasta que la persona toque "Confirmar".
 */
@Component
public class BakeyToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(BakeyToolRegistry.class);

    private final Map<String, BakeyTool> tools = new LinkedHashMap<>();
    private final BakeyActionService actionService;

    public BakeyToolRegistry(BakeyQueryTools queryTools, BakeyActionTools actionTools, BakeyActionService actionService) {
        this.actionService = actionService;
        for (BakeyTool tool : queryTools.tools()) tools.put(tool.name(), tool);
        for (BakeyTool tool : actionTools.tools()) tools.put(tool.name(), tool);
    }

    /** Declaraciones de las herramientas que esta persona puede usar (lo que se le manda al modelo). */
    public List<AiTool> toolsFor(ToolContext ctx) {
        return tools.values().stream().filter(t -> t.allowedFor(ctx)).map(BakeyTool::declaration).toList();
    }

    /**
     * Ejecuta lo que pidió el modelo. Nunca tira excepción: si algo sale mal, al modelo le llega
     * {"error": "…"} en español para que se lo explique a la persona.
     * readOnly: acá solo se LEE (las consultas, y las acciones solo se revisan y se proponen).
     */
    @Transactional(readOnly = true)
    public ToolOutcome execute(AiToolCall call, ToolContext ctx) {
        BakeyTool tool = tools.get(call.name());
        if (tool == null) {
            return ToolOutcome.of(Map.of("error", "No existe la herramienta " + call.name() + "."));
        }
        if (!tool.allowedFor(ctx)) {
            log.warn("Bakey: {} pidió la herramienta {} sin permiso", ctx.user().getEmail(), call.name());
            return ToolOutcome.of(Map.of("error", "Esta persona no tiene permiso para hacer o ver eso."));
        }
        try {
            log.info("Bakey: {} usa {} {}", ctx.user().getEmail(), call.name(), call.args());
            ToolArgs args = new ToolArgs(call.args());
            if (tool.kind() == BakeyTool.Kind.CONSULTA) {
                return ToolOutcome.of(tool.handler().apply(ctx, args));
            }
            // ACCIÓN: se revisa y se deja pendiente. NO se cambia ningún dato acá.
            ActionProposal proposal = tool.proposer().apply(ctx, args);
            ToolOutcome.ActionCard card = actionService.propose(ctx, tool, proposal);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("estado", "esperando_confirmacion");
            result.put("accion", proposal.title());
            result.put("resumen", proposal.lines());
            result.put("instruccion", "Esto TODAVÍA NO SE HIZO. La persona tiene que revisar la tarjeta y tocar "
                    + "«Confirmar» (vence a las " + card.expiresAt() + "). No digas que ya está hecho.");
            return new ToolOutcome(result, card);
        } catch (ToolArgs.InvalidArgException e) {
            return ToolOutcome.of(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Bakey: falló la herramienta {}", call.name(), e);
            return ToolOutcome.of(Map.of("error", "No se pudo consultar esa información en este momento."));
        }
    }
}
