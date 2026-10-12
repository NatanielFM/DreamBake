package com.dreambake.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * Regla del negocio: los montos (totales, señas, precios, costos y márgenes) solo los ve el ADMIN.
 *
 * "Oculto" = hay una sesión de PASTELERO que no es ADMIN. Los clientes de la tienda no entran acá: ven
 * el total de su propio pedido, que es suyo.
 *
 * Lo usan MoneyResponseAdvice (saca estos campos de la respuesta) y los controladores de pedidos e
 * inventario (para que el pastelero no pise por error un monto al guardar).
 */
public final class MoneyVisibility {

    // Nombres de los campos de dinero que pueden aparecer en las respuestas del pastelero
    // (pedido, ítem del pedido, producto, insumo y receta, también cuando vienen anidados).
    public static final Set<String> MONEY_FIELDS = Set.of(
            "totalPrice", "deposit", "price", "unitCost", "estimatedCost", "costComplete");

    private MoneyVisibility() {
    }

    /** ¿La persona de esta sesión es PASTELERO sin ser ADMIN? (entonces no puede ver montos) */
    public static boolean hiddenForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        boolean admin = false;
        boolean baker = false;
        for (var authority : auth.getAuthorities()) {
            if ("ADMIN".equals(authority.getAuthority())) admin = true;
            if ("PASTELERO".equals(authority.getAuthority())) baker = true;
        }
        return baker && !admin;
    }
}
