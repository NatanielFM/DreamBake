package com.dreambake.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // Peso máximo de una foto subida desde el panel.
    // Spring trae 1 MB por defecto y las fotos de un celular pesan entre 2 y 5 MB, así que
    // se rechazaban. Igual, el panel achica la foto antes de mandarla (gallery.js).
    // Se configura acá y no en application.properties (ese archivo tiene la conexión local).
    public static final int MAX_UPLOAD_MB = 10;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(MAX_UPLOAD_MB));
        factory.setMaxRequestSize(DataSize.ofMegabytes(MAX_UPLOAD_MB + 1));
        return factory.createMultipartConfig();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expone la carpeta física "uploads" al navegador
        Path uploadDir = Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + uploadPath + "/");
    }
}
