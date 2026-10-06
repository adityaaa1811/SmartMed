package com.smartmed.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.smartmed.security.AuthenticationRateLimitFilter;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(SmartMedProperties.class)
public class AppConfig {
    @Bean
    FilterRegistrationBean<AuthenticationRateLimitFilter> disableContainerRateLimitRegistration(
            AuthenticationRateLimitFilter filter) {
        FilterRegistrationBean<AuthenticationRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    Clock applicationClock(SmartMedProperties properties) {
        return Clock.system(ZoneId.of(properties.getTimezone()));
    }
}
