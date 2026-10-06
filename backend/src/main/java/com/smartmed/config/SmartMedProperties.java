package com.smartmed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartmed")
public class SmartMedProperties {

    private final Api api = new Api();
    private final Interaction interaction = new Interaction();
    private final Security security = new Security();
    private String timezone = "Asia/Kolkata";

    public Api getApi() {
        return api;
    }

    public Interaction getInteraction() {
        return interaction;
    }

    public Security getSecurity() {
        return security;
    }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

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
        private final Cors cors = new Cors();
        private final RateLimit rateLimit = new RateLimit();

        public Jwt getJwt() { return jwt; }
        public Cors getCors() { return cors; }
        public RateLimit getRateLimit() { return rateLimit; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String value) { allowedOrigins = value; }
    }

    public static class RateLimit {
        private int loginFailures = 10;
        private int registrationRequests = 8;
        private long windowSeconds = 900;
        private int maxTrackedKeys = 10000;
        public int getLoginFailures() { return loginFailures; }
        public void setLoginFailures(int value) { loginFailures = value; }
        public int getRegistrationRequests() { return registrationRequests; }
        public void setRegistrationRequests(int value) { registrationRequests = value; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long value) { windowSeconds = value; }
        public int getMaxTrackedKeys() { return maxTrackedKeys; }
        public void setMaxTrackedKeys(int value) { maxTrackedKeys = value; }
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
