package com.dreambake.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uso de Bakey en el día (paso 5.6), para la tarjeta "Uso de Bakey" del Resumen del admin:
 * cuántos mensajes recibió, cuántas acciones propuso y cuántas se confirmaron, y cuántos errores hubo
 * (Google sin cupo o caído, demoras, respuestas fallidas).
 *
 * Son contadores en memoria que vuelven a cero cada día (hora de Argentina) y al reiniciar la app.
 * Alcanza para ver de un vistazo si Bakey se usa y si el cupo gratis de Gemini alcanza; el detalle de
 * cada acción confirmada queda igual en el Registro de actividad (auditoría, "vía Bakey").
 */
@Component
public class BakeyUsageStats {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private LocalDate day = LocalDate.now(ZONE);
    private int messages;
    private int proposed;
    private int confirmed;
    private int errors;
    private int quota;   // de los errores, cuántos fueron por cupo agotado / Google sobrecargado

    // synchronized: varias personas pueden hablar con Bakey a la vez
    private synchronized void rollDay() {
        LocalDate today = LocalDate.now(ZONE);
        if (!today.equals(day)) {
            day = today;
            messages = proposed = confirmed = errors = quota = 0;
        }
    }

    public synchronized void message() { rollDay(); messages++; }
    public synchronized void proposed(int count) { rollDay(); proposed += count; }
    public synchronized void confirmed() { rollDay(); confirmed++; }
    public synchronized void error(boolean quotaProblem) {
        rollDay();
        errors++;
        if (quotaProblem) quota++;
    }

    public synchronized Map<String, Object> today() {
        rollDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dia", day.toString());
        m.put("mensajes", messages);
        m.put("accionesPropuestas", proposed);
        m.put("accionesConfirmadas", confirmed);
        m.put("errores", errors);
        m.put("erroresPorCupo", quota);
        return m;
    }
}
