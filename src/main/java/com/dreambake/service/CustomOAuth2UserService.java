package com.dreambake.service;

import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Igual que CustomOidcUserService, pero para redes sociales que usan OAuth2 "simple" (sin OpenID
 * Connect), como Facebook. Google normalmente entra por la variante OIDC; este camino
 * solo se usa con Google si se configura sin el permiso "openid".
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private SocialLoginService socialLoginService;

    // Qué datos le pedimos a Facebook: id, nombre, email (puede no venir) y la foto de perfil.
    // Spring Boot por defecto pide "id,name,email" a una versión vieja de la API; acá se suma la foto.
    static final String FACEBOOK_USER_INFO_URI =
            "https://graph.facebook.com/v21.0/me?fields=id,name,email,picture.width(200).height(200)";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if ("facebook".equals(registrationId)) {
            userRequest = withFacebookFields(userRequest);
        }
        OAuth2User socialUser = super.loadUser(userRequest); // datos que mandó la red social
        String nameKey = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
        Map<String, Object> attrs = socialUser.getAttributes();

        User user;
        if ("google".equals(registrationId)) {
            // Datos de Google: sub (id), email, email_verified, name, picture
            user = socialLoginService.resolve(
                    AuthProvider.GOOGLE,
                    text(attrs.get("sub")),
                    text(attrs.get("email")),
                    isTrue(attrs.get("email_verified")),
                    text(attrs.get("name")),
                    text(attrs.get("picture")));
        } else if ("facebook".equals(registrationId)) {
            // Datos de Facebook: id, name, email (NO viene si la cuenta se creó con teléfono) y picture.
            // Facebook solo entrega emails que la persona confirmó, por eso se toman como verificados.
            // Igual, si es el email de alguien del personal, SocialLoginService lo rechaza.
            user = socialLoginService.resolve(
                    AuthProvider.FACEBOOK,
                    text(attrs.get("id")),
                    text(attrs.get("email")),
                    true,
                    text(attrs.get("name")),
                    facebookPicture(attrs.get("picture")));
        } else {
            throw new OAuth2AuthenticationException(new OAuth2Error(SocialLoginService.ERROR_FAILED));
        }

        return new SocialUserPrincipal.Plain(user.getEmail(), user.getRoles(), attrs, nameKey);
    }

    // Si la registración usa la dirección de Facebook de siempre, se cambia por la nuestra (con la foto).
    // Si apunta a otro lado (por ejemplo un Facebook simulado para probar), se respeta.
    private static OAuth2UserRequest withFacebookFields(OAuth2UserRequest request) {
        ClientRegistration registration = request.getClientRegistration();
        String uri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        if (uri == null || !uri.startsWith("https://graph.facebook.com/")) {
            return request;
        }
        ClientRegistration withPicture = ClientRegistration.withClientRegistration(registration)
                .userInfoUri(FACEBOOK_USER_INFO_URI)
                .build();
        return new OAuth2UserRequest(withPicture, request.getAccessToken(), request.getAdditionalParameters());
    }

    // La foto llega como {"data": {"url": "...", "is_silhouette": false}}.
    // "is_silhouette" = true es la silueta gris de Facebook (sin foto): no se guarda.
    private static String facebookPicture(Object picture) {
        if (picture instanceof Map<?, ?> p && p.get("data") instanceof Map<?, ?> data) {
            if (Boolean.TRUE.equals(data.get("is_silhouette"))) return null;
            return text(data.get("url"));
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    // email_verified puede llegar como true o como "true"
    private static boolean isTrue(Object value) {
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }
}
