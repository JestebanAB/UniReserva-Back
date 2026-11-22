package com.unireserva.back.config;

import java.time.Clock;
import java.time.ZoneId;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Reloj único del sistema. Las reservas se manejan en hora local de la universidad. */
    @Bean
    public Clock clock(@Value("${app.timezone:America/Bogota}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        long maxImageSize = 5L * 1024 * 1024;
        long maxRequestSize = maxImageSize + 1024L * 1024;
        return new MultipartConfigElement("", maxImageSize, maxRequestSize, 0);
    }
}
