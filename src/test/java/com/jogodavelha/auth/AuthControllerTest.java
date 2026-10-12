package com.jogodavelha.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345",
        "jwt.expiration=86400000"
})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;

    private static final String REGISTER_OK =
            "{\"username\":\"user\",\"password\":\"segredo123\",\"email\":\"user@example.com\"}";
    private static final String LOGIN_OK =
            "{\"identifier\":\"user\",\"password\":\"segredo123\"}";

    private User user() {
        User u = new User("user", "hash", "user@example.com");
        u.setId(UUID.randomUUID());
        return u;
    }

    @Test
    void registerReturns201WithoutPassword() throws Exception {
        when(authService.register(any())).thenReturn(user());

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_OK))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerDuplicateUsernameReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException("Username já está em uso"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username já está em uso"));
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("E-mail já está em uso"));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_OK))
                .andExpect(status().isConflict());
    }

    @Test
    void registerInvalidBodyReturns400WithFieldMessages() throws Exception {
        String invalid = "{\"username\":\"ab\",\"password\":\"123\",\"email\":\"nao-e-email\"}";

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").value("Username deve ter entre 3 e 15 caracteres"))
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void loginReturns200WithTokenAndProfile() throws Exception {
        User u = user();
        when(authService.login(any())).thenReturn(new LoginResult("jwt-token", u));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.id").value(u.getId().toString()))
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.coinsBalance").value(0));
    }

    @Test
    void loginInvalidCredentialsReturns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Credenciais inválidas"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_OK))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciais inválidas"));
    }

    @Test
    void loginBlankFieldsReturn400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.identifier").exists());
    }
}