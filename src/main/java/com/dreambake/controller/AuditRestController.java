package com.dreambake.controller;

import com.dreambake.service.AuditIntegrityService;
import com.dreambake.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de la auditoría (solo ADMIN: cae en la regla general de /api/** de SecurityConfig).
 * La pantalla completa de auditoría se hace en el paso 3.4.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditRestController {

    @Autowired
    private AuditIntegrityService integrityService;

    // Revisar la cadena ahora (sin esperar al lunes). Si está alterada, avisa igual que la tarea semanal.
    @PostMapping("/verify")
    public ResponseEntity<AuditService.ChainCheck> verifyNow() {
        return ResponseEntity.ok(integrityService.check("pedido manual desde el panel"));
    }
}
