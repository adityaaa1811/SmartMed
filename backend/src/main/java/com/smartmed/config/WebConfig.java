package com.smartmed.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SmartMedProperties properties;

    public WebConfig(SmartMedProperties properties) { this.properties = properties; }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.getSecurity().getCors().getAllowedOrigins().split(",");
        for (int i = 0; i < origins.length; i++) origins[i] = origins[i].trim();
        if (origins.length == 0 || java.util.Arrays.stream(origins).anyMatch(String::isBlank)
                || java.util.Arrays.asList(origins).contains("*")) {
            throw new IllegalStateException("Configure explicit CORS origins; wildcard origins are not allowed with credentials");
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
