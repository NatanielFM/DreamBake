package com.dreambake.service;

import com.dreambake.entity.AuthProvider;
import com.dreambake.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Se ejecuta cuando Google devuelve al usuario a DreamBake (login con OpenID Connect).
 * Spring ya validó la respuesta de Google; acá se leen sus datos y se busca o crea
 * el usuario de DreamBake con las reglas de SocialLoginService.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    @Autowired
    private SocialLoginService socialLoginService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser googleUser = super.loadUser(userRequest); // datos que mandó Google

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"google".equals(registrationId)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(SocialLoginService.ERROR_FAILED));
        }

        User user = socialLoginService.resolve(
                AuthProvider.GOOGLE,
                googleUser.getSubject(),                              // id fijo del usuario en Google
                googleUser.getEmail(),
                Boolean.TRUE.equals(googleUser.getEmailVerified()),   // ¿Google confirmó el email?
                googleUser.getFullName(),
                googleUser.getPicture());

        return new SocialUserPrincipal.Oidc(user.getEmail(), user.getRoles(),
                googleUser.getIdToken(), googleUser.getUserInfo());
    }
}
