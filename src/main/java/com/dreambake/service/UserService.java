package com.dreambake.service;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.User;
import com.dreambake.repository.ReminderRepository;
import com.dreambake.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Registro de auditoría (paso 3.3)
    @Autowired
    private AuditService auditService;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private com.dreambake.repository.PasswordResetTokenRepository resetTokenRepository;

    public void registerUser(User user) {
        // Ciframos la contraseña antes de guardarla
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        // Por defecto le asignamos el rol de usuario. Además se fuerza un usuario NUEVO
        // (sin id) y habilitado, aunque el formulario mande otra cosa
        user.setId(null);
        user.setEnabled(true);
        user.setEmail(user.getEmail().trim());
        user.setRoles("ROLE_USER");
        // Guardamos en la base de datos
        User saved = userRepository.save(user);
        auditService.log(AuditAction.CREATE, AuditModule.SEGURIDAD, "Usuario", saved.getId(),
                "Se registró un cliente nuevo con email y contraseña ("
                        + com.dreambake.audit.AuditText.maskEmail(saved.getEmail()) + ")", null);
    }

    /**
     * Crea un usuario que entra con Google o Facebook (lo usan los pasos 2.2 y 2.3).
     * No tiene contraseña propia, pero la columna password es obligatoria y las validaciones
     * actuales la exigen. Por eso se guarda el hash BCrypt de un UUID aleatorio que nadie conoce:
     * así nadie puede entrar con usuario y contraseña a esa cuenta, y no se rompe nada existente.
     */
    public User createSocialUser(String email, String name, AuthProvider provider,
                                 String providerId, String avatarUrl) {
        User user = new User();
        user.setEmail(email.trim());
        user.setName(name);
        user.setAuthProvider(provider);
        user.setProviderId(providerId);
        user.setAvatarUrl(avatarUrl);
        user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        user.setRoles("ROLE_USER");
        user.setEnabled(true);
        // El email ya está comprobado: lo confirmó la red social o la persona escribió el código
        // que le mandamos (Facebook sin email, SocialSignupController)
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        auditService.log(AuditAction.CREATE, AuditModule.SEGURIDAD, "Usuario", saved.getId(),
                "Se creó una cuenta de cliente con " + (provider == AuthProvider.FACEBOOK ? "Facebook" : "Google")
                        + " (" + com.dreambake.audit.AuditText.maskEmail(saved.getEmail()) + ")", null);
        return saved;
    }

    public java.util.List<User> getPasteleros() {
        return userRepository.findByRoles("PASTELERO");
    }

    public User savePastelero(User user) {
        if (user.getId() != null) {
            User existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            user.setCreatedAt(existing.getCreatedAt());
            // El formulario no trae estos datos: se conservan los que ya tenía
            user.setAuthProvider(existing.getAuthProvider());
            user.setProviderId(existing.getProviderId());
            user.setAvatarUrl(existing.getAvatarUrl());
            user.setLastLoginAt(existing.getLastLoginAt());
            user.setEmailVerified(existing.getEmailVerified());
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(existing.getPassword());
            } else {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        } else {
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        }
        user.setRoles("PASTELERO");
        return userRepository.save(user);
    }

    /**
     * Da de baja a un usuario. Antes borra sus recordatorios (paso 5.2): son notas personales y,
     * si quedaran, la base no dejaría borrar al usuario (apuntan a él).
     * Todo en una transacción: si algo falla, no se borra nada.
     * @return cuántos recordatorios se borraron (para la auditoría)
     */
    @Transactional
    public int deleteUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return 0;
        int reminders = reminderRepository.deleteByOwner(user);
        resetTokenRepository.deleteByUser(user);   // links de "Olvidé mi contraseña" (paso 6.1)
        userRepository.delete(user);
        return reminders;
    }
}