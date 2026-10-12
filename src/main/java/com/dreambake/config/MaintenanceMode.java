package com.dreambake.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Modo mantenimiento" (paso 4.3): mientras se restaura una copia de seguridad, el resto del sitio
 * responde "En mantenimiento" (MaintenanceFilter). Así nadie carga un pedido o mueve stock justo
 * mientras se están cambiando las tablas de la base.
 *
 * Es solo una marca en memoria (AtomicBoolean: segura aunque la lean muchos pedidos a la vez).
 * La activa y la desactiva RestoreService, siempre en un try/finally para que nunca quede trabada.
 */
@Component
public class MaintenanceMode {

    private final AtomicBoolean active = new AtomicBoolean(false);

    public boolean isActive() {
        return active.get();
    }

    public void start() {
        active.set(true);
    }

    public void stop() {
        active.set(false);
    }
}
