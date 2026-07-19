package com.feng.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ImageStorageConfig implements WebMvcConfigurer {
    @Value("${image.upload-dir:./uploads}") private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**").addResourceLocations(root.toUri().toString());
        registry.addResourceHandler("/img/**").addResourceLocations(root.resolve("images").toUri().toString());
    }
}
