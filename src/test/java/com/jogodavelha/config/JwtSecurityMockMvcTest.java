package com.jogodavelha.config;

import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = JwtSecurityMockMvcTest.ProtectedController.class, properties = {
        "jwt.secret=this-is-a-very-long-secret-key-with-at-least-32-bytes",
        "jwt.expiration=86400000"
})
@Import({SecurityConfig.class, JwtSecurityMockMvcTest.TestBeans.class, JwtSecurityMockMvcTest.ProtectedController.class})
class JwtSecurityMockMvcTest {
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

    @TestConfiguration
    static class TestBeans {
        @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() { return new com.fasterxml.jackson.databind.ObjectMapper(); }
        @Bean JwtService testJwtService(JwtProperties properties, java.time.Clock clock) {
            return new JwtService(properties, clock);
        }
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/test/protected")
        UUID principal(Authentication authentication) { return (UUID) authentication.getPrincipal(); }
    }

    private String token() {
        User user = new User("testuser", "hashed-password", "test@example.com");
        user.setId(USER_ID);
        return jwtService.generateToken(user);
    }

    @Test
    void protectedRouteRequiresValidBearerToken() throws Exception {
        mvc.perform(get("/test/protected").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(USER_ID.toString()));
        mvc.perform(get("/test/protected")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void invalidAndUnsupportedAuthorizationDoNotAuthenticate() throws Exception {
        for (String header : new String[]{"Bearer abc", "Basic xxx", "Bearer "}) {
            mvc.perform(get("/test/protected").header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void publicRoutesRemainAccessibleWithInvalidToken() throws Exception {
        for (String path : new String[]{"/auth/login", "/auth/register", "/api/health", "/v3/api-docs", "/ws/info"}) {
            mvc.perform(get(path)).andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status == 401 || status == 403) throw new AssertionError("Unexpected status " + status + " for " + path);
            });
        }
        mvc.perform(get("/api/health").header("Authorization", "Bearer invalid"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) throw new AssertionError("Public route rejected token");
                });
    }
}
