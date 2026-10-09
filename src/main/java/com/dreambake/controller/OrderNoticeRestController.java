package com.dreambake.controller;

import com.dreambake.service.OrderNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Avisos de pedidos del panel de administración. Está bajo /api/**, que en SecurityConfig es solo
 * para ADMIN (a propósito NO está bajo /api/orders: el mensaje incluye precios y el pastelero no los ve).
 */
@RestController
@RequestMapping("/api/order-notices")
public class OrderNoticeRestController {

    @Autowired
    private OrderNoticeService orderNoticeService;

    // "Avisar por WhatsApp": devuelve el link wa.me con el mensaje armado según el estado del pedido
    @GetMapping("/{orderId}/whatsapp")
    public ResponseEntity<?> whatsapp(@PathVariable Long orderId) {
        return orderNoticeService.whatsappFor(orderId)
                .<ResponseEntity<?>>map(w -> ResponseEntity.ok(Map.of("url", w.url(), "message", w.message())))
                .orElse(ResponseEntity.badRequest().body(Map.of("message",
                        "El teléfono de este pedido no sirve para WhatsApp. Revisalo en la ficha del pedido.")));
    }

    // Corre ahora lo mismo que la tarea de las 8:00 (recordatorios y resumen de entregas), sin esperar la hora.
    // No repite lo que ya se mandó.
    @PostMapping("/run-daily")
    public OrderNoticeService.DailySummary runDaily() {
        return orderNoticeService.runDaily("botón del panel");
    }
}
