package com.dreambake.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

/**
 * El "usuario logueado" cuando se entra con una red social.
 *
 * Dos detalles importantes:
 *  - getName() devuelve el EMAIL GUARDADO EN DREAMBAKE (no el de Google). Toda la app usa
 *    authentication.getName() como email (perfil, currentUser, último ingreso…), así que con esto
 *    todo funciona igual que con usuario y contraseña, aunque un empleado haya vinculado otro Gmail.
 *  - Los permisos (authorities) salen de User.roles, igual que en UserDetailsServiceImpl: un ADMIN
 *    que entra con Google tiene exactamente los mismos permisos y va al mismo panel.
 */
public final class SocialUserPrincipal {

    private SocialUserPrincipal() {}

    // Mismo criterio que UserDetailsServiceImpl (.authorities(user.getRoles()))
    static List<GrantedAuthority> authoritiesFrom(String roles) {
        return AuthorityUtils.createAuthorityList(roles != null ? roles : "");
    }

    /** Variante OIDC (la que usa Google: "Iniciar sesión con Google" usa OpenID Connect). */
    public static class Oidc extends DefaultOidcUser {
        private final String email;

        public Oidc(String email, String roles, OidcIdToken idToken, OidcUserInfo userInfo) {
            super(authoritiesFrom(roles), idToken, userInfo);
            this.email = email;
        }

        @Override
        public String getName() {
            return email;
        }
    }

    /** Variante OAuth2 "simple" (sin OIDC; la va a usar Facebook en el paso 2.3). */
    public static class Plain extends DefaultOAuth2User {
        private final String email;

        public Plain(String email, String roles, Map<String, Object> attributes, String nameAttributeKey) {
            super(authoritiesFrom(roles), attributes, nameAttributeKey);
            this.email = email;
        }

        @Override
        public String getName() {
            return email;
        }
    }
}
