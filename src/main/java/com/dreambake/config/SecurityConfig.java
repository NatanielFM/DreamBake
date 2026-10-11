package com.dreambake.config;

import com.dreambake.service.CustomOAuth2UserService;
import com.dreambake.service.CustomOidcUserService;
import com.dreambake.service.LoginAttemptService;
import com.dreambake.service.PendingSocialSignup;
import com.dreambake.service.SocialLoginService;
import com.dreambake.util.ClientIpResolver;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.Set;
import java.util.UUID;

/**
 * Configuración de seguridad de DreamBake.
 *
 * Resumen de las medidas (explicadas una por una más abajo):
 *  1. Rutas: la tienda es pública; el panel y la API interna exigen rol.
 *     De la API de pedidos solo es público "crear pedido" (lo usa el checkout).
 *  2. CSRF activado: cada envío (POST/PUT/DELETE) lleva un token que solo
 *     conoce nuestra propia página. Evita que otro sitio haga acciones en
 *     nombre de un usuario logueado.
 *  3. Encabezados de seguridad: política de contenido (CSP), no permitir que
 *     el sitio se muestre dentro de un iframe ajeno, referrer limitado.
 *  4. Login: bloqueo temporal después de varios intentos fallidos (por email + IP).
 *  5. Límite de pedidos, cupones, registros e inicios de sesión desde una misma IP (RateLimitFilter).
 *  6. Cookie de sesión con SameSite=Lax.
 *  7. "Mantener la sesión iniciada" (remember-me) con una cookie firmada que dura 14 días.
 *  8. Login con Google y Facebook (solo las redes que tengan credenciales en config/application.properties).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LoginAttemptService loginAttemptService;
    private final UserDetailsService userDetailsService;
    private final String rememberMeKey;

    // La clave que firma la cookie de "Mantener la sesión iniciada" se toma de la propiedad
    // dreambake.remember-me-key (en producción, de la variable de entorno DREAMBAKE_REMEMBER_ME_KEY,
    // ver application-prod.properties). Si no está configurada, se genera una al azar cada vez que
    // arranca la app: funciona igual, pero al reiniciar el servidor hay que volver a iniciar sesión.
    public SecurityConfig(LoginAttemptService loginAttemptService, UserDetailsService userDetailsService,
            @Value("${dreambake.remember-me-key:}") String configuredKey) {
        this.loginAttemptService = loginAttemptService;
        this.userDetailsService = userDetailsService;
        this.rememberMeKey = configuredKey.isBlank() ? UUID.randomUUID().toString() : configuredKey;
    }

    // Política de contenido: de dónde puede cargar cosas el navegador.
    // Scripts: solo archivos .js de nuestro sitio y de jsDelivr (Chart.js). Ya no se permite
    // código JavaScript escrito dentro del HTML (onclick="…", <script>…</script>): si alguien
    // lograra meter código en una página, el navegador no lo ejecutaría.
    // Estilos: se mantiene 'unsafe-inline' porque todavía hay atributos style="…" (no ejecutan código).
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://cdn.jsdelivr.net",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            // Fotos de perfil de las cuentas que entran con Google (lh3.googleusercontent.com)
            // y con Facebook (platform-lookaside.fbsbx.com y *.fbcdn.net)
            "img-src 'self' data: blob: https://lh3.googleusercontent.com https://platform-lookaside.fbsbx.com https://*.fbcdn.net",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            // accounts.google.com: el botón "Vincular cuenta de Google" del perfil es un formulario que
            // termina redirigiendo a Google, y el navegador también controla esas redirecciones
            "form-action 'self' https://accounts.google.com",
            "frame-ancestors 'none'");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> socialClients,
            CustomOidcUserService customOidcUserService,
            CustomOAuth2UserService customOAuth2UserService) throws Exception {
        http
                // 2. CSRF: el token viaja en la cookie XSRF-TOKEN y el JS lo devuelve en el encabezado
                //    X-XSRF-TOKEN (ver static/js/csrf.js). Los formularios con th:action lo incluyen solos.
                .csrf(csrf -> csrf.spa())

                // 1. Quién puede entrar a cada ruta
                .authorizeHttpRequests(auth -> auth
                        // Tienda pública y archivos estáticos
                        // /baja: página para darse de baja de los emails promocionales (link de cada email)
                        // /privacidad y /eliminar-datos: páginas legales (Facebook las exige para publicar la app)
                        // /login/completar-email: pedir el email cuando Facebook no lo manda (SocialSignupController)
                        .requestMatchers("/privacidad", "/eliminar-datos", "/login/completar-email", "/login/completar-email/**").permitAll()
                        .requestMatchers("/", "/builder", "/products", "/cart", "/register", "/login", "/baja",
                                "/css/**", "/js/**", "/img/**", "/uploads/**", "/error").permitAll()

                        // API que usa la tienda sin iniciar sesión
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()          // crear pedido (checkout)
                        .requestMatchers(HttpMethod.GET, "/api/cake-config/public").permitAll() // opciones del creador
                        .requestMatchers(HttpMethod.GET, "/api/coupons/validate/**").permitAll() // ver si un cupón sirve

                        // Chequeo de salud (¿está viva la app?): solo responde UP o DOWN. Es el único endpoint
                        // de actuator que se expone (ver management.endpoints en config/application.properties)
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()

                        // Paneles
                        .requestMatchers("/admin/**").hasAuthority("ADMIN")
                        .requestMatchers("/baker/**").hasAuthority("PASTELERO")

                        // "Quién descargó qué" (informe, lista de compras, recibos): lo avisan los dos paneles
                        .requestMatchers(HttpMethod.POST, "/api/audit/download").hasAnyAuthority("ADMIN", "PASTELERO")

                        // API que usan el panel del pastelero y el del admin
                        .requestMatchers("/api/orders/**", "/api/inventory/**", "/api/recipes/**").hasAnyAuthority("ADMIN", "PASTELERO")
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").hasAnyAuthority("ADMIN", "PASTELERO")

                        // Área del cliente ("Mis pedidos"): cualquier usuario logueado, y cada uno ve solo lo suyo
                        // (AccountRestController toma el usuario de la sesión, nunca de un parámetro)
                        .requestMatchers("/api/account/**").authenticated()

                        // Todo lo demás de la API (usuarios, clientes, cupones, galería, productos, configuración
                        // del creador…) es solo para el administrador
                        .requestMatchers("/api/**").hasAuthority("ADMIN")

                        .anyRequest().authenticated())

                // 3. Encabezados de seguridad
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))

                // 5. Límite de pedidos y cupones por IP (antes del login)
                .addFilterBefore(new RateLimitFilter(), UsernamePasswordAuthenticationFilter.class)

                // 4. Login con bloqueo por intentos fallidos
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler())
                        .failureHandler(authenticationFailureHandler())
                        .permitAll())
                // 7. "Mantener la sesión iniciada": si se marca la casilla del login, el navegador guarda
                //    una cookie firmada (con la clave de abajo y la contraseña cifrada del usuario) que
                //    vuelve a iniciar la sesión durante 14 días. Si el usuario cambia la contraseña,
                //    la cookie deja de servir.
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(14 * 24 * 60 * 60)
                        .userDetailsService(userDetailsService))
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll());

        // 8. Login con Google. Spring Boot solo arma la lista de redes sociales (ClientRegistrationRepository)
        //    si en config/application.properties hay un client-id cargado. Sin credenciales no se activa
        //    (y el botón "Continuar con Google" no se muestra, ver PublicViewAdvice).
        ClientRegistrationRepository clients = socialClients.getIfAvailable();
        if (clients != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login") // nuestra página, no la genérica de Spring
                    .authorizationEndpoint(endpoint -> endpoint
                            .authorizationRequestResolver(socialAuthorizationRequestResolver(clients)))
                    .userInfoEndpoint(info -> info
                            .oidcUserService(customOidcUserService)   // Google (OpenID Connect)
                            .userService(customOAuth2UserService))    // Facebook (paso 2.3)
                    .successHandler(authenticationSuccessHandler())   // misma redirección por rol
                    .failureHandler(oauth2FailureHandler()));
        }

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // Venía de "Vincular cuenta de Google" en el perfil: vuelve al perfil con el aviso
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(SocialLoginService.SESSION_LINK_DONE) != null) {
                session.removeAttribute(SocialLoginService.SESSION_LINK_DONE);
                // Se vuelve a dejar la sesión "con contraseña" que tenía el empleado antes de ir a Google,
                // así puede desvincular enseguida si se equivocó de cuenta
                Object previous = session.getAttribute(SocialLoginService.SESSION_LINK_PREVIOUS_AUTH);
                if (previous instanceof Authentication previousAuth) {
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(previousAuth);
                    SecurityContextHolder.setContext(context);
                    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
                }
                SocialLoginService.clearLinkRequest(session);
                response.sendRedirect("/profile?linked=google");
                return;
            }
            SocialLoginService.clearLinkRequest(session);

            // Login correcto: se borran los intentos fallidos de ese email desde esta IP
            loginAttemptService.loginSucceeded(authentication.getName(), ClientIpResolver.resolve(request));

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ADMIN"));
            boolean isBaker = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("PASTELERO"));

            if (isAdmin) {
                response.sendRedirect("/admin");
            } else if (isBaker) {
                response.sendRedirect("/baker");
            } else {
                response.sendRedirect("/");
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            // Cuenta bloqueada por intentos: se avisa con un mensaje distinto
            if (exception instanceof LockedException) {
                response.sendRedirect("/login?blocked");
                return;
            }
            loginAttemptService.loginFailed(request.getParameter("username"), ClientIpResolver.resolve(request));
            response.sendRedirect("/login?error");
        };
    }

    // Arma el pedido que se manda a Google o Facebook. A Facebook solo se le piden los permisos
    // "email" y "public_profile" (nombre y foto), aunque en la configuración se escriba otra cosa:
    // así no se pide más de lo necesario y Meta no exige una revisión extra de la app.
    private OAuth2AuthorizationRequestResolver socialAuthorizationRequestResolver(ClientRegistrationRepository clients) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clients, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(builder -> builder.attributes(attrs -> {
            if ("facebook".equals(attrs.get(OAuth2ParameterNames.REGISTRATION_ID))) {
                builder.scopes(Set.of("email", "public_profile"));
            }
        }));
        return resolver;
    }

    // Códigos de error del login con Google que tienen un mensaje propio en login.html y profile.html.
    // Cualquier otro error se muestra como "failed" (nunca se copia a la dirección lo que mande Google).
    private static final Set<String> SOCIAL_ERROR_CODES = Set.of(
            SocialLoginService.ERROR_STAFF_PASSWORD, SocialLoginService.ERROR_EMAIL_UNVERIFIED,
            SocialLoginService.ERROR_DISABLED, SocialLoginService.ERROR_ALREADY_LINKED);

    private AuthenticationFailureHandler oauth2FailureHandler() {
        return (request, response, exception) -> {
            String code = SocialLoginService.ERROR_FAILED;
            if (exception instanceof OAuth2AuthenticationException oauthError) {
                String raw = oauthError.getError().getErrorCode();
                // Facebook no mandó el email: no es un error, se le pide a la persona (con código de verificación)
                if (SocialLoginService.ERROR_EMAIL_REQUIRED.equals(raw)
                        && request.getSession(false) != null
                        && request.getSession(false).getAttribute(PendingSocialSignup.SESSION_KEY) != null) {
                    response.sendRedirect("/login/completar-email");
                    return;
                }
                if ("access_denied".equals(raw)) {
                    code = "cancelled"; // el usuario tocó "Cancelar" en la pantalla de Google
                } else if (SOCIAL_ERROR_CODES.contains(raw)) {
                    code = raw;
                }
            }
            // Si era un pedido de "Vincular" desde el perfil, se vuelve al perfil (la sesión sigue abierta)
            HttpSession session = request.getSession(false);
            boolean wasLinking = session != null && session.getAttribute(SocialLoginService.SESSION_LINK_USER_ID) != null;
            SocialLoginService.clearLinkRequest(session);
            // De qué red social volvió (la dirección termina en /google o /facebook): el aviso la nombra
            String via = request.getRequestURI().endsWith("/facebook") ? "facebook" : "google";
            response.sendRedirect(wasLinking ? "/profile?linkError=" + code : "/login?social=" + code + "&via=" + via);
        };
    }

    // 6. La cookie de sesión no se envía en pedidos que vienen de otros sitios (SameSite=Lax)
    @Bean
    public CookieSameSiteSupplier sessionCookieSameSite() {
        return CookieSameSiteSupplier.ofLax();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
