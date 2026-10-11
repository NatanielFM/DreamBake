package com.dreambake.audit;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método para que quede en el registro de auditoría cuando termina bien (AuditAspect).
 *
 * Ejemplo:
 *
 *     @Auditable(action = AuditAction.CREATE, module = AuditModule.CUPONES, entityType = "Cupón",
 *                entityId = "#body.id", description = "Creó el cupón #{#body.code}")
 *     public Coupon createCoupon(@RequestBody Coupon coupon) { ... }
 *
 * entityId y description pueden usar datos del método con expresiones de Spring (SpEL):
 *  - los parámetros por su nombre (#id, #request…),
 *  - #result: lo que devolvió el método, y #body: lo mismo pero sin el "sobre" ResponseEntity.
 * En description, las expresiones van entre #{ y }; el resto es texto normal.
 *
 * ¿Cuándo NO se usa? Cuando hace falta el "antes" y el "después" (editar un pedido, ajustar stock,
 * cambiar un precio): la anotación solo ve el método cuando ya terminó, y para entonces el dato
 * viejo ya se pisó. En esos casos se llama a AuditService directamente desde el código.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    AuditAction action();

    AuditModule module();

    /** Qué tipo de cosa se tocó, en español: "Cupón", "Insumo"… */
    String entityType() default "";

    /** Expresión que da el id de lo afectado (por ejemplo "#id" o "#body.id"). Vacío = sin id. */
    String entityId() default "";

    /** Frase en español para la pantalla de auditoría. Puede llevar expresiones #{…}. */
    String description();
}
