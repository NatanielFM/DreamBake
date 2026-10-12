package com.dreambake.config;

import com.dreambake.service.FileStorageService;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;
import org.springframework.web.servlet.resource.VersionResourceResolver;

import java.time.Duration;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // Peso máximo de una foto subida desde el panel (paso 6.7: 5 MB; FileStorageService lo vuelve a revisar).
    // El panel achica la foto antes de mandarla (gallery.js), así que una foto normal pesa mucho menos.
    // Se configura acá y no en application.properties (ese archivo tiene la conexión local).
    public static final int MAX_UPLOAD_MB = 5;

    // Caché del navegador para archivos estáticos (paso 6.7): una semana
    private static final Duration ONE_WEEK = Duration.ofDays(7);

    private final Environment env;

    public MvcConfig(Environment env) {
        this.env = env;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(MAX_UPLOAD_MB));
        factory.setMaxRequestSize(DataSize.ofMegabytes(MAX_UPLOAD_MB + 1));
        return factory.createMultipartConfig();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl week = CacheControl.maxAge(ONE_WEEK).cachePublic();

        // Fotos subidas (carpeta física "uploads"). Tienen nombre UUID y no cambian nunca: se pueden guardar
        // una semana sin problema.
        String uploadPath = FileStorageService.uploadDir().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/")
                .setCacheControl(week);

        // CSS, JS e imágenes del sitio: también una semana, pero con "huella" en el nombre
        // (ej: /css/main-3f2a1c….css). La huella sale del contenido del archivo: si se cambia el CSS,
        // cambia el nombre y el navegador baja el nuevo. Thymeleaf la agrega sola en th:href / th:src
        // (@{/css/main.css}) gracias a ResourceUrlEncodingFilter (abajo). Las rutas sin huella también
        // funcionan (por ejemplo una imagen nombrada desde el JS).
        // Con DevTools (al programar) no se guarda la huella en memoria, así se ve cada cambio al recargar.
        boolean cacheResolved = env.getProperty("spring.web.resources.chain.cache", Boolean.class, true);
        for (String folder : new String[]{"css", "js", "img"}) {
            registry.addResourceHandler("/" + folder + "/**")
                    .addResourceLocations("classpath:/static/" + folder + "/")
                    .setCacheControl(week)
                    .resourceChain(cacheResolved)
                    .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
        }
    }

    // Reescribe los links de las plantillas (@{/css/…}, @{/js/…}, @{/img/…}) con la versión con huella
    @Bean
    public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new ResourceUrlEncodingFilter();
    }
}
