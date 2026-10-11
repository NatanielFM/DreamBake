package com.dreambake.controller;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.repository.OrderRepository;
import com.dreambake.service.AuditIntegrityService;
import com.dreambake.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de la auditoría. /verify es solo ADMIN (regla general de /api/** de SecurityConfig);
 * /download lo usan los dos paneles (ADMIN y PASTELERO). La pantalla completa es el paso 3.4.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditRestController {

    @Autowired
    private AuditIntegrityService integrityService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private OrderRepository orderRepository;

    // Revisar la cadena ahora (sin esperar al lunes). Si está alterada, avisa igual que la tarea semanal.
    @PostMapping("/verify")
    public ResponseEntity<AuditService.ChainCheck> verifyNow() {
        return ResponseEntity.ok(integrityService.check("pedido manual desde el panel"));
    }

    public record DownloadRequest(String type, Long orderId) {}

    /**
     * "Quién descargó qué". El informe, la lista de compras y los recibos se arman en el navegador
     * (con imprimir / guardar como PDF), así que el servidor no se entera solo: el panel avisa acá
     * justo antes de abrirlos. Solo se aceptan estos tipos; el texto lo arma el servidor.
     */
    @PostMapping("/download")
    public ResponseEntity<Void> registerDownload(@RequestBody DownloadRequest req) {
        String type = req == null || req.type() == null ? "" : req.type();
        switch (type) {
            case "informe" -> auditService.log(AuditAction.EXPORT, AuditModule.SISTEMA, "Informe", null,
                    "Descargó el informe del resumen (PDF)", null);
            case "lista-compras" -> auditService.log(AuditAction.DOWNLOAD, AuditModule.INVENTARIO, "Lista de compras", null,
                    "Descargó la lista de compras (PDF)", null);
            case "recibo" -> {
                if (req.orderId() == null) return ResponseEntity.badRequest().build();
                String label = orderRepository.findById(req.orderId()).map(AuditText::order).orElse("#" + req.orderId());
                auditService.log(AuditAction.DOWNLOAD, AuditModule.PEDIDOS, "Pedido", req.orderId(),
                        "Imprimió el recibo del pedido " + label, null);
            }
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.noContent().build();
    }
}
