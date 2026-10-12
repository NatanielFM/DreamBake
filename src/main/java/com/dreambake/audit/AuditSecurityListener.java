package com.dreambake.audit;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Auditoría de la seguridad (paso 3.3): inicios de sesión, intentos fallidos y cierres de sesión.
 *
 * Spring Security avisa con "eventos" cada vez que pasa algo de esto (igual que en
 * LoginTimestampListener). Acá se escuchan y se anotan. Se usa logAs() con el email a mano porque,
 * en el momento del evento, la sesión todavía no está armada (login) o ya se borró (logout).
 */
@Component
public class AuditSecurityListener {

    private final AuditService auditService;

    public AuditSecurityListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onLogin(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth instanceof AnonymousAuthenticationToken) return;
        String how;
        if (auth instanceof RememberMeAuthenticationToken) {
            how = " (sesión recordada con \"Mantener la sesión iniciada\")";
        } else if (auth instanceof OAuth2AuthenticationToken t) {
            how = " con " + network(t.getAuthorizedClientRegistrationId());
        } else if (auth instanceof OAuth2LoginAuthenticationToken t) {
            how = " con " + network(t.getClientRegistration().getRegistrationId());
        } else {
            how = " con su contraseña";
        }
        auditService.logAs(auth.getName(), AuditAction.LOGIN, AuditModule.SEGURIDAD, "Usuario", null,
                "Inició sesión" + how, null);
    }

    @EventListener
    public void onLoginFailed(AbstractAuthenticationFailureEvent event) {
        String attempted = event.getAuthentication() == null ? null : event.getAuthentication().getName();
        Exception reason = event.getException();
        String why;
        if (reason instanceof LockedException) {
            why = "bloqueado por demasiados intentos";
        } else if (reason instanceof DisabledException) {
            why = "la cuenta está deshabilitada";
        } else if (reason instanceof BadCredentialsException) {
            why = "email o contraseña incorrectos";
        } else if (reason instanceof OAuth2AuthenticationException o) {
            why = "rechazado al entrar con red social (" + o.getError().getErrorCode() + ")";
        } else {
            why = reason == null ? "motivo desconocido" : reason.getClass().getSimpleName();
        }
        // El email intentado puede no existir: se anota igual (sirve para ver si alguien prueba cuentas)
        String email = attempted == null || attempted.isBlank() ? null : attempted.trim();
        auditService.logAs(email, AuditAction.LOGIN_FAILED, AuditModule.SEGURIDAD, "Usuario", null,
                "Intento fallido de inicio de sesión" + (email != null ? " con el email " + email : "") + ": " + why,
                Map.of("emailIntentado", email == null ? "" : email, "motivo", why));
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return;
        auditService.logAs(auth.getName(), AuditAction.LOGOUT, AuditModule.SEGURIDAD, "Usuario", null,
                "Cerró sesión", null);
    }

    private static String network(String registrationId) {
        return "facebook".equals(registrationId) ? "Facebook" : "google".equals(registrationId) ? "Google" : registrationId;
    }
}
