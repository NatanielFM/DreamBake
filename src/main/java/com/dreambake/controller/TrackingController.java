package com.dreambake.controller;

import com.dreambake.service.TrackingService;
import com.dreambake.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Página pública /seguimiento (paso 6.5): el cliente escribe su código y ve el estado del pedido.
 * También se abre directo con el link de los emails: /seguimiento?codigo=DB-7K3P9Q.
 * Es un GET con el código en la dirección para que el link funcione; el código no es un dato personal.
 */
@Controller
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/seguimiento")
    public String tracking(@RequestParam(value = "codigo", required = false) String code,
                           HttpServletRequest request, HttpServletResponse response, Model model) {
        if (code == null || code.isBlank()) {
            return "seguimiento";   // solo el formulario
        }
        model.addAttribute("codigo", code.trim().length() > 20 ? code.trim().substring(0, 20) : code.trim());
        // Límite de 10 consultas por minuto por IP (solo cuentan las búsquedas, no abrir la página)
        if (trackingService.tooManyLookups(ClientIpResolver.resolve(request))) {
            response.setStatus(429);
            model.addAttribute("error", "Hiciste muchas consultas seguidas. Esperá un minuto y probá de nuevo.");
            return "seguimiento";
        }
        trackingService.find(code).ifPresentOrElse(
                view -> model.addAttribute("pedido", view),
                () -> model.addAttribute("error", "No encontramos un pedido con ese código. Revisalo: tiene la forma DB-7K3P9Q."));
        return "seguimiento";
    }
}
