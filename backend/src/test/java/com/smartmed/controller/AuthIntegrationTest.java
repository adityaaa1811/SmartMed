package com.smartmed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.entity.User;
import com.smartmed.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String JWT_TEST_SECRET = "test-jwt-secret-minimum-32-characters-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void registerSuccess() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Test User", email, "SmartMed@123", "PATIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.user.role").value("PATIENT"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }


    @Test
    void publicRegistrationCannotClaimCaregiverOrDoctorRoles() throws Exception {
        for (String role : new String[] {"DOCTOR", "CAREGIVER"}) {
            String email = uniqueEmail();
            MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson("Untrusted Role", email, "SmartMed@123", role)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.user.role").value("PATIENT"))
                    .andReturn();
            assertThat(userRepository.findByEmail(email.toLowerCase()).orElseThrow().getRole().name())
                    .isEqualTo("PATIENT");
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("data").path("user").path("role").asText()).isEqualTo("PATIENT");
        }
    }


    @Test
    void corsPreflightAllowsConfiguredDevelopmentOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void registerDuplicateEmailConflict() throws Exception {
        String email = uniqueEmail();
        registerUser("User One", email, "SmartMed@123", "PATIENT");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("User Two", email, "SmartMed@456", "CAREGIVER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void registerInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Test", "not-an-email", "SmartMed@123", "PATIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerWeakPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Test", uniqueEmail(), "short", "PATIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void loginSuccess() throws Exception {
        String email = uniqueEmail();
        String password = "SmartMed@123";
        registerUser("Login User", email, password, "DOCTOR");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("DOCTOR"));
    }

    @Test
    void loginWrongPassword() throws Exception {
        String email = uniqueEmail();
        registerUser("User", email, "SmartMed@123", "PATIENT");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPass1!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    void loginUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("missing@example.com", "SmartMed@123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginEmailCaseNormalization() throws Exception {
        String email = uniqueEmail();
        registerUser("Case User", email.toUpperCase(), "SmartMed@123", "PATIENT");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email.toUpperCase(), "SmartMed@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.user.role").value("PATIENT"));
    }

    @Test
    void validJwtAuthenticatesMeEndpoint() throws Exception {
        String token = registerAndGetToken("Me User", uniqueEmail(), "SmartMed@123", "PATIENT");

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void jwtSubjectMustMatchTheAccountLoadedFromEmailClaim() throws Exception {
        String email = uniqueEmail();
        registerUser("Bound Subject", email, "SmartMed@123", "PATIENT");
        User account = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        SecretKey key = Keys.hmacShaKeyFor(JWT_TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithAnotherAccountSubject = Jwts.builder()
                .subject(String.valueOf(account.getId() + 1000))
                .claim("email", account.getEmail())
                .claim("role", account.getRole().name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenWithAnotherAccountSubject))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void jwtWithoutExpirationIsRejected() throws Exception {
        String email = uniqueEmail();
        registerUser("Missing Expiration", email, "SmartMed@123", "PATIENT");
        User account = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        SecretKey key = Keys.hmacShaKeyFor(JWT_TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutExpiration = Jwts.builder()
                .subject(String.valueOf(account.getId()))
                .claim("email", account.getEmail())
                .claim("role", account.getRole().name())
                .issuedAt(Date.from(Instant.now()))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenWithoutExpiration))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredJwtRejected() throws Exception {
        String email = uniqueEmail();
        registerUser("Expired", email, "SmartMed@123", "PATIENT");
        User user = userRepository.findByEmail(email.toLowerCase()).orElseThrow();

        SecretKey key = Keys.hmacShaKeyFor(JWT_TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void invalidJwtRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void meWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void passwordStoredAsBcryptHash() throws Exception {
        String email = uniqueEmail();
        registerUser("Hash User", email, "SmartMed@123", "PATIENT");
        User user = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$")).isTrue();
    }

    @Test
    void userResponseNeverContainsPassword() throws Exception {
        String token = registerAndGetToken("Safe User", uniqueEmail(), "SmartMed@123", "CAREGIVER");
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.toString()).doesNotContain("password");
        assertThat(root.at("/data/password").isMissingNode()).isTrue();
    }

    private void registerUser(String fullName, String email, String password, String role) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(fullName, email, password, role)))
                .andExpect(status().isCreated());
        promoteRole(email, role);
    }

    private String registerAndGetToken(String fullName, String email, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(fullName, email, password, role)))
                .andExpect(status().isCreated())
                .andReturn();
        promoteRole(email, role);
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private static String uniqueEmail() {
        return "user." + UUID.randomUUID() + "@example.com";
    }

    private static String registerJson(String fullName, String email, String password, String role) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(fullName, email, password, role);
    }

    private static String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
    private void promoteRole(String email, String role) {
        if ("PATIENT".equals(role)) return;
        User user = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        user.setRole(com.smartmed.entity.Role.valueOf(role));
        userRepository.save(user);
    }

}
