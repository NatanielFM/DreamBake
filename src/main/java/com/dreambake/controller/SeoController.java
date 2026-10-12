package com.dreambake.controller;

import com.dreambake.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * robots.txt y sitemap.xml para los buscadores (paso 6.3).
 *
 * Se arman en el código (no son archivos fijos) porque necesitan la dirección pública del sitio
 * (app.public-url): así sirven igual en la PC de desarrollo y con el dominio real.
 */
@RestController
public class SeoController {

    // Páginas públicas que conviene que Google encuentre (las de cuenta, carrito y recuperar contraseña no)
    private static final List<String> PUBLIC_PAGES = List.of(
            "/", "/products", "/builder", "/seguimiento", "/privacidad", "/terminos", "/eliminar-datos");

    private final AppProperties app;

    public SeoController(AppProperties app) {
        this.app = app;
    }

    // Qué partes del sitio NO deben recorrer los buscadores: los paneles, la API y la cuenta del cliente.
    // (No es una medida de seguridad: esas rutas ya piden iniciar sesión; solo evita que aparezcan en Google.)
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String text = """
                User-agent: *
                Disallow: /admin
                Disallow: /baker
                Disallow: /api/
                Disallow: /profile
                Disallow: /recuperar
                Disallow: /login/completar-email

                Sitemap: %s/sitemap.xml
                """.formatted(app.getPublicUrl());
        return ResponseEntity.ok(text);
    }

    // Lista de páginas públicas en el formato que leen Google y Bing
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String today = LocalDate.now().toString();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String page : PUBLIC_PAGES) {
            xml.append("  <url><loc>").append(app.getPublicUrl()).append(page).append("</loc>")
               .append("<lastmod>").append(today).append("</lastmod></url>\n");
        }
        xml.append("</urlset>\n");
        return ResponseEntity.ok(xml.toString());
    }
}
