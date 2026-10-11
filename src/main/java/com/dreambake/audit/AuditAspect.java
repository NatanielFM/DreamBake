package com.dreambake.audit;

import com.dreambake.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspecto de auditoría (paso 3.3). Un "aspecto" es código que Spring ejecuta automáticamente
 * alrededor de otros métodos sin tocarlos: acá, DESPUÉS de cada método marcado con @Auditable que
 * terminó bien, arma la descripción y llama a AuditService.log().
 *
 * POR QUÉ HAY DOS FORMAS DE REGISTRAR (para la tesis):
 *  - @Auditable (este aspecto) para los casos simples: crear, borrar, cambiar una configuración.
 *    Es una línea arriba del método, no ensucia la lógica y es difícil olvidarse de un caso.
 *  - Llamar a AuditService directamente cuando hace falta el ANTES y el DESPUÉS (editar un pedido,
 *    ajustar stock, cambiar un precio) o cuando la descripción depende de lo que pasó adentro (si se
 *    forzó el stock). El aspecto solo ve el método desde afuera, cuando ya terminó: el dato viejo ya
 *    se pisó y no lo puede recuperar.
 *
 * "Terminó bien" = no tiró excepción y, si devuelve un ResponseEntity, el código es 2xx. Un
 * badRequest o un notFound no se registran (no cambió nada).
 *
 * @Order(HIGHEST_PRECEDENCE + 10): este aspecto envuelve por FUERA a la transacción (@Transactional,
 * que tiene el orden más bajo), así se ejecuta cuando la transacción ya se confirmó: si el guardado
 * fallara, no se registra nada. No puede ser HIGHEST_PRECEDENCE "a secas": Spring necesita que antes
 * corra su ExposeInvocationInterceptor (orden HIGHEST_PRECEDENCE + 1); si no, el aspecto falla y la
 * acción termina en error aunque se haya guardado (lo vimos al probar).
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void afterSuccess(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            if (result instanceof ResponseEntity<?> response && !response.getStatusCode().is2xxSuccessful()) {
                return; // la acción no se hizo (error de validación, no encontrado…)
            }
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            // Contexto de las expresiones: los parámetros por su nombre, #result y #body
            MethodBasedEvaluationContext context =
                    new MethodBasedEvaluationContext(null, method, joinPoint.getArgs(), parameterNames);
            context.setVariable("result", result);
            context.setVariable("body", result instanceof ResponseEntity<?> r ? r.getBody() : result);

            Object entityId = auditable.entityId().isBlank() ? null
                    : parser.parseExpression(auditable.entityId()).getValue(context);
            Expression description = parser.parseExpression(auditable.description(), new TemplateParserContext());
            String text = description.getValue(context, String.class);

            auditService.log(auditable.action(), auditable.module(),
                    auditable.entityType().isBlank() ? null : auditable.entityType(), entityId, text, null);
        } catch (Exception e) {
            // Un problema armando la descripción nunca tiene que afectar la acción del usuario
            log.error("No se pudo registrar la auditoría de {}: {}", joinPoint.getSignature().toShortString(), e.getMessage());
        }
    }
}
