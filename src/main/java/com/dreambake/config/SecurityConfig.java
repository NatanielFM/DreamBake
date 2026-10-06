package com.dreambake.config;

import com.dreambake.service.LoginAttemptService;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

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
 *  4. Login: bloqueo temporal después de varios intentos fallidos.
 *  5. Límite de pedidos/cupones por minuto desde una misma IP (RateLimitFilter).
 *  6. Cookie de sesión con SameSite=Lax.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LoginAttemptService loginAttemptService;

    public SecurityConfig(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    // Política de contenido: de dónde puede cargar cosas el navegador.
    // 'unsafe-inline' se mantiene porque las páginas todavía usan scripts y estilos en línea
    // (onclick, <script> en el HTML); el resto queda limitado a nuestro sitio y a los CDN que usamos.
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob: https://via.placeholder.com",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 2. CSRF: el token viaja en la cookie XSRF-TOKEN y el JS lo devuelve en el encabezado
                //    X-XSRF-TOKEN (ver static/js/csrf.js). Los formularios con th:action lo incluyen solos.
                .csrf(csrf -> csrf.spa())

                // 1. Quién puede entrar a cada ruta
                .authorizeHttpRequests(auth -> auth
                        // Tienda pública y archivos estáticos
                        .requestMatchers("/", "/builder", "/products", "/cart", "/register", "/login",
                                "/css/**", "/js/**", "/img/**", "/uploads/**", "/error").permitAll()

                        // API que usa la tienda sin iniciar sesión
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()          // crear pedido (checkout)
                        .requestMatchers(HttpMethod.GET, "/api/cake-config/public").permitAll() // opciones del creador
                        .requestMatchers(HttpMethod.GET, "/api/coupons/validate/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/coupons/consume/**").permitAll()

                        // Paneles
                        .requestMatchers("/admin/**").hasAuthority("ADMIN")
                        .requestMatchers("/baker/**").hasAuthority("PASTELERO")

                        // API que usan el panel del pastelero y el del admin
                        .requestMatchers("/api/orders/**", "/api/inventory/**", "/api/recipes/**").hasAnyAuthority("ADMIN", "PASTELERO")
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").hasAnyAuthority("ADMIN", "PASTELERO")

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
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID")
                        .permitAll());

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // Login correcto: se borran los intentos fallidos de ese email
            loginAttemptService.loginSucceeded(authentication.getName());

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
            loginAttemptService.loginFailed(request.getParameter("username"));
            response.sendRedirect("/login?error");
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
