package com.example.wgwarden.auth;

import com.example.wgwarden.AbstractIntegrationTest;
import com.example.wgwarden.config.AppProperties;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthApiTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    AppProperties properties;

    @Test
    void registerLoginAndAccessProtectedEndpoint() throws Exception {
        mockMvc.perform(register("User@Example.com", "password1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String token = tokenFrom(mockMvc.perform(login("user@example.com", "password1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(blankOrNullString())))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void emailIsCaseInsensitiveAndNormalized() throws Exception {
        mockMvc.perform(register("Mixed.Case@Example.com", "password1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("mixed.case@example.com"));

        mockMvc.perform(register("MIXED.CASE@EXAMPLE.COM", "password1"))
                .andExpect(status().isConflict());

        mockMvc.perform(login("MIXED.CASE@example.COM", "password1"))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        mockMvc.perform(register("dup@example.com", "password1"))
                .andExpect(status().isCreated());

        mockMvc.perform(register("dup@example.com", "password1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email already registered"));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(register("wrongpass@example.com", "password1"))
                .andExpect(status().isCreated());

        mockMvc.perform(login("wrongpass@example.com", "nope-nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid email or password"));
    }

    @Test
    void loginWithUnknownEmailReturnsSameUnauthorizedError() throws Exception {
        mockMvc.perform(login("nobody@example.com", "password1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid email or password"));
    }

    @Test
    void passwordIsStoredAsArgon2Hash() throws Exception {
        mockMvc.perform(register("hash@example.com", "password1"))
                .andExpect(status().isCreated());

        User user = users.findByEmail("hash@example.com").orElseThrow();
        assertThat(user.getPasswordHash())
                .startsWith("$argon2")
                .doesNotContain("password1");
    }

    @Test
    void invalidRegisterBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(register("not-an-email", "short"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(register("shortpass@example.com", "1234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", startsWith("password ")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid request body"));
    }

    @Test
    void invalidJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedJwtReturnsUnauthorized() throws Exception {
        String token = tokenFrom(mockMvc.perform(register("tampered@example.com", "password1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserReturnsUnauthorized() throws Exception {
        String token = tokenFrom(mockMvc.perform(register("deleted@example.com", "password1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        UUID id = users.findByEmail("deleted@example.com").orElseThrow().getId();
        users.deleteById(id);

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenDoesNotBlockPublicEndpoints() throws Exception {
        mockMvc.perform(register("stale@example.com", "password1"))
                .andExpect(status().isCreated());

        mockMvc.perform(login("stale@example.com", "password1")
                        .header("Authorization", "Bearer expired-or-garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpointsDoNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownApiRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/servers"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder register(String email, String password) {
        return post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password));
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password));
    }

    private static String tokenFrom(String responseBody) {
        return JsonPath.read(responseBody, "$.token");
    }
}
