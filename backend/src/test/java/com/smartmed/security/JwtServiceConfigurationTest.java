package com.smartmed.security;

import com.smartmed.config.SmartMedProperties;
import com.smartmed.exception.JwtConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceConfigurationTest {
    @Test
    void productionRejectsExampleSecretsWithoutEchoingThem() {
        SmartMedProperties properties = new SmartMedProperties();
        properties.getSecurity().getJwt().setSecret("CHANGE_ME_GENERATE_BASE64_RANDOM_SECRET");
        MockEnvironment environment = productionEnvironment();
        JwtConfigurationException failure = org.junit.jupiter.api.Assertions.assertThrows(
                JwtConfigurationException.class,
                () -> new JwtService(properties, environment).initSigningKey());
        assertThat(failure.getMessage()).doesNotContain("CHANGE_ME");
    }

    @Test
    void localConfigurationRejectsFormerDocumentedSecretExample() {
        SmartMedProperties properties = new SmartMedProperties();
        properties.getSecurity().getJwt().setSecret("your-local-secret-at-least-32-characters-long");
        assertThatThrownBy(() -> new JwtService(properties, new MockEnvironment()).initSigningKey())
                .isInstanceOf(JwtConfigurationException.class);
    }

    @Test
    void productionRequiresAtLeast32Base64DecodedBytes() {
        SmartMedProperties properties = new SmartMedProperties();
        properties.getSecurity().getJwt().setSecret("base64:" + Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(() -> new JwtService(properties, productionEnvironment()).initSigningKey())
                .isInstanceOf(JwtConfigurationException.class);
    }

    private static MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    @Test
    void productionAcceptsExternallySupplied32ByteRandomBase64Key() {
        SmartMedProperties properties = new SmartMedProperties();
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        properties.getSecurity().getJwt().setSecret("base64:" + Base64.getEncoder().encodeToString(key));
        new JwtService(properties, productionEnvironment()).initSigningKey();
    }

    @Test
    void productionRejectsAllZeroBase64Key() {
        SmartMedProperties properties = new SmartMedProperties();
        properties.getSecurity().getJwt().setSecret("base64:" + Base64.getEncoder().encodeToString(new byte[32]));
        assertThatThrownBy(() -> new JwtService(properties, productionEnvironment()).initSigningKey())
                .isInstanceOf(JwtConfigurationException.class);
    }

    @Test
    void productionRejectsMalformedBase64() {
        SmartMedProperties properties = new SmartMedProperties();
        properties.getSecurity().getJwt().setSecret("base64:not-valid-base64%%");
        assertThatThrownBy(() -> new JwtService(properties, productionEnvironment()).initSigningKey())
                .isInstanceOf(JwtConfigurationException.class);
    }

    @Test
    void productionRejectsRepeatedPatternKey() {
        SmartMedProperties properties = new SmartMedProperties();
        byte[] repeated = "abcdabcdabcdabcdabcdabcdabcdabcd".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        properties.getSecurity().getJwt().setSecret("base64:" + Base64.getEncoder().encodeToString(repeated));
        assertThatThrownBy(() -> new JwtService(properties, productionEnvironment()).initSigningKey())
                .isInstanceOf(JwtConfigurationException.class);
    }
}
