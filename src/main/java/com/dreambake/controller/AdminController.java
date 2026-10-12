package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditIntegrityService;
import com.dreambake.service.BackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// Este controlador es el encargado de gestionar todas las peticiones que llegan bajo la ruta /admin.
// Su única responsabilidad es escuchar la petición del usuario y devolver la vista correspondiente.

@Controller // Le dice a Spring: "Esta clase es un componente que va a manejar peticiones
            // web"
@RequestMapping("/admin") // Le indica a Spring: que todos los métodos que estén dentro de esta clase
// van a empezar con la dirección /admin. Si creo un método test sería
// /admin/test
public class AdminController {

    @Autowired
    private AuditIntegrityService auditIntegrityService;

    @Autowired
    private BackupService backupService;

    @GetMapping // Define el tipo de petición. Cuando un usuario escriba www.dominio.com/admin
                // en su
    // navegador el sistema ejecutará este método porque es una petición tipo GET
    public String adminPanel(Model model) {
        // Aviso de seguridad (paso 3.2): si la revisión encontró el registro de auditoría alterado,
        // se muestra arriba de todo hasta que el administrador toque "Entendido"
        model.addAttribute("auditAlert", auditIntegrityService.pendingAlert());
        // Aviso de copias de seguridad (paso 4.2): más de 48 horas sin un backup bueno, o backups desactivados.
        // Se muestra en rojo en el Resumen; desaparece solo cuando vuelve a haber un backup bueno.
        model.addAttribute("backupWarning", backupService.staleWarning());
        return "admin"; // Esto busca admin.html en /templates
    }

    // Botón "Entendido" del aviso de auditoría
    @PostMapping("/auditoria/entendido")
    @Auditable(action = AuditAction.UPDATE, module = AuditModule.SEGURIDAD, entityType = "Auditoría",
            description = "Marcó como vista la alerta de que el registro de auditoría fue alterado")
    public String acknowledgeAuditAlert() {
        auditIntegrityService.acknowledge();
        return "redirect:/admin";
    }
}
