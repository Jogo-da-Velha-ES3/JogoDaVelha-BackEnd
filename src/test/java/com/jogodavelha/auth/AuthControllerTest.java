package com.jogodavelha.auth;

import com.jogodavelha.config.ApiPrefixConfig;
import com.jogodavelha.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiPrefixConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345",
        "jwt.expiration=86400000"
})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";

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

        mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_OK))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerDuplicateUsernameReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException("Username já está em uso"));

        mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Username já está em uso"))
                .andExpect(jsonPath("$.path").value(REGISTER_URL))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("E-mail já está em uso"));

        mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("E-mail já está em uso"));
    }

    @Test
    void registerInvalidBodyReturns400WithFieldMessages() throws Exception {
        String invalid = "{\"username\":\"ab\",\"password\":\"123\",\"email\":\"nao-e-email\"}";

        mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Dados inválidos"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.username").value("Username deve ter entre 3 e 15 caracteres"))
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void registerMalformedJsonReturns400() throws Exception {
        mvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content("{nao-e-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corpo da requisição inválido"));
    }

    @Test
    void loginReturns200WithTokenAndProfile() throws Exception {
        User u = user();
        when(authService.login(any())).thenReturn(new LoginResult("jwt-token", u));

        mvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(LOGIN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.id").value(u.getId().toString()))
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.coinsBalance").value(0));
    }

    @Test
    void loginInvalidCredentialsReturns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Credenciais inválidas"));

        mvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(LOGIN_OK))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"))
                .andExpect(jsonPath("$.path").value(LOGIN_URL));
    }

    @Test
    void loginBlankFieldsReturn400() throws Exception {
        mvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.identifier").exists());
    }
}
