package com.dreambake.service;

import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Igual que CustomOidcUserService, pero para redes sociales que usan OAuth2 "simple" (sin OpenID
 * Connect), como Facebook (paso 2.3). Google normalmente entra por la variante OIDC; este camino
 * solo se usa con Google si se configura sin el permiso "openid".
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private SocialLoginService socialLoginService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User socialUser = super.loadUser(userRequest); // datos que mandó la red social
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
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
        } else {
            // Facebook se agrega en el paso 2.3
            throw new OAuth2AuthenticationException(new OAuth2Error(SocialLoginService.ERROR_FAILED));
        }

        return new SocialUserPrincipal.Plain(user.getEmail(), user.getRoles(), attrs, nameKey);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    // email_verified puede llegar como true o como "true"
    private static boolean isTrue(Object value) {
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }
}
