package com.dreambake.controller;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.dreambake.entity.User;
import com.dreambake.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserRestController {

    @Autowired
    private UserService userService;

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    @Autowired
    private com.dreambake.repository.UserRepository userRepository;

    // Obtener solo usuarios con rol PASTELERO
    @GetMapping("/pasteleros")
    public List<User> getPasteleros() {
        return userService.getPasteleros();
    }

    // Crear o actualizar un pastelero
    @PostMapping("/pasteleros")
    public ResponseEntity<?> savePastelero(@RequestBody User user) {
        try {
            // Antes de guardar: ¿es alta o edición? ¿qué rol y datos tenía? ¿se cambia la contraseña?
            User before = user.getId() == null ? null : userRepository.findById(user.getId()).orElse(null);
            Map<String, Object> was = before == null ? null : teamSnapshot(before);
            String roleBefore = before == null ? null : before.getRoles();
            boolean passwordChanged = before != null && user.getPassword() != null && !user.getPassword().isEmpty();

            User saved = userService.savePastelero(user);

            if (before == null) {
                audit.log(AuditAction.CREATE, AuditModule.EQUIPO, "Usuario", saved.getId(),
                        "Dio de alta al pastelero " + saved.getName() + " (" + saved.getEmail() + ")",
                        AuditText.change(null, teamSnapshot(saved)));
            } else {
                Map<String, Object> diff = AuditText.diff(was, teamSnapshot(saved));
                audit.log(AuditAction.UPDATE, AuditModule.EQUIPO, "Usuario", saved.getId(),
                        "Editó los datos del pastelero " + saved.getName() + AuditText.changedFields(diff), diff);
                if (!Objects.equals(roleBefore, saved.getRoles())) {
                    audit.log(AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", saved.getId(),
                            "Cambió el rol de " + saved.getName() + " de «" + roleBefore + "» a «" + saved.getRoles() + "»",
                            AuditText.change(Map.of("rol", String.valueOf(roleBefore)), Map.of("rol", saved.getRoles())));
                }
                if (passwordChanged) {
                    audit.log(AuditAction.UPDATE, AuditModule.SEGURIDAD, "Usuario", saved.getId(),
                            "Cambió la contraseña del pastelero " + saved.getName(), null);
                }
            }
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Datos de un miembro del equipo que se comparan en la auditoría (nunca la contraseña)
    private Map<String, Object> teamSnapshot(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", u.getName());
        m.put("email", u.getEmail());
        m.put("telefono", u.getPhone());
        m.put("rol", u.getRoles());
        m.put("habilitado", u.getEnabled());
        return m;
    }

    // Eliminar un usuario
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            User before = userRepository.findById(id).orElse(null);
            userService.deleteUser(id);
            if (before != null) {
                audit.log(AuditAction.DELETE, AuditModule.EQUIPO, "Usuario", id,
                        "Dio de baja a " + before.getName() + " (" + before.getEmail() + ", rol " + before.getRoles() + ")",
                        AuditText.change(teamSnapshot(before), null));
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
