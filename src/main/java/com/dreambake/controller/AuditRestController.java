package com.dreambake.controller;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.User;
import com.dreambake.repository.OrderRepository;
import com.dreambake.repository.UserRepository;
import com.dreambake.service.AuditReportService;
import com.dreambake.service.AuditIntegrityService;
import com.dreambake.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API de la auditoría. Todo es solo ADMIN (regla general de /api/** de SecurityConfig), salvo
 * POST /download, que lo usan los dos paneles (ADMIN y PASTELERO).
 *
 * IMPORTANTE (paso 3.4): acá NO hay ningún PUT ni DELETE, ni ningún otro endpoint que modifique o
 * borre registros de auditoría. Solo se puede consultar, descargar y verificar la cadena.
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

    @Autowired
    private AuditReportService reportService;

    @Autowired
    private UserRepository userRepository;

    private static final java.time.format.DateTimeFormatter FILE_STAMP =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    // ===================== Pestaña "Registro de actividad" (paso 3.4) =====================

    // Los filtros llegan como parámetros de la dirección (?from=2026-10-01&to=…&actor=…&module=…&action=…&q=…).
    // Los enums mal escritos responden 400 solos.
    private static AuditReportService.Filter filter(LocalDate from, LocalDate to, String actor,
                                                    AuditModule module, AuditAction action, String q) {
        return new AuditReportService.Filter(from, to, actor, module, action, q);
    }

    // Una página (50 registros, los más recientes primero) con los filtros aplicados en la base
    @GetMapping("/logs")
    public AuditReportService.Result logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditModule module,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String q) {
        return reportService.search(filter(from, to, actor, module, action, q), page);
    }

    // Opciones del filtro "Usuario"
    @GetMapping("/actors")
    public java.util.List<AuditReportService.Actor> actors() {
        return reportService.actors();
    }

    // Descarga en CSV (Excel en español) con los filtros aplicados. La descarga se registra a sí misma.
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditModule module,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String q) {
        AuditReportService.Filter f = filter(from, to, actor, module, action, q);
        AuditReportService.Export export = reportService.csv(f);
        logExport("CSV (Excel)", f, export);
        return file(export.bytes(), "registro-actividad-" + LocalDateTime.now().format(FILE_STAMP) + ".csv",
                new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
    }

    // Descarga en PDF con logo, datos de quién lo generó y el sello de integridad en el pie
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditModule module,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String q,
            Authentication auth) {
        AuditReportService.Filter f = filter(from, to, actor, module, action, q);
        User me = userRepository.findByEmail(auth.getName()).orElse(null);
        String generatedBy = me == null ? auth.getName() : me.getName() + " (" + me.getEmail() + ")";
        AuditReportService.Export export = reportService.pdf(f, generatedBy);
        logExport("PDF", f, export);
        return file(export.bytes(), "registro-actividad-" + LocalDateTime.now().format(FILE_STAMP) + ".pdf",
                MediaType.APPLICATION_PDF);
    }

    // Cada descarga queda en el propio registro: quién, qué formato, con qué filtros y el sello
    private void logExport(String format, AuditReportService.Filter f, AuditReportService.Export export) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("formato", format);
        details.put("registros", export.rows());
        details.put("totalConFiltros", export.total());
        details.put("fechas", f.rangeText());
        details.put("filtros", f.otherFiltersText());
        details.put("selloRegistro", export.lastId());
        details.put("selloHash", export.lastHash());
        auditService.log(AuditAction.EXPORT, AuditModule.SEGURIDAD, "Registro de actividad", null,
                "Descargó el registro de actividad en " + format + " (" + export.rows()
                        + (export.rows() == 1 ? " registro; " : " registros; ") + f.rangeText().toLowerCase() + "; " + f.otherFiltersText() + ")",
                details);
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, String name, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(name).build());
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }

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
