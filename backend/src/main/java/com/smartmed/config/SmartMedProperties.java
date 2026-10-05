package com.smartmed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartmed")
public class SmartMedProperties {

    private final Cors cors = new Cors();
    private final Api api = new Api();
    private final Interaction interaction = new Interaction();
    private final Security security = new Security();

    public Cors getCors() {
        return cors;
    }

    public Api getApi() {
        return api;
    }

    public Interaction getInteraction() {
        return interaction;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Api {
        private String version = "v1";

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    public static class Interaction {
        private String provider = "mock";
        private String apiKey = "";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Security {
        private final Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
        }
    }

    public static class Jwt {
        private String secret = "";
        private long expirationMs = 86400000L;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }
    }
}
