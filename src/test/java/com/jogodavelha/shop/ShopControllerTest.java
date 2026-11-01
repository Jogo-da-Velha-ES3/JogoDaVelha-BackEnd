package com.jogodavelha.shop;

import com.jogodavelha.config.ApiPrefixConfig;
import com.jogodavelha.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ShopController.class, properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345", "jwt.expiration=86400000"})
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiPrefixConfig.class, GlobalExceptionHandler.class})
class ShopControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ShopService shop;
    private final UUID userId = UUID.randomUUID(), itemId = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken(userId, null, List.of());

    @Test
    void purchaseUsesTheAuthenticatedIdentityAndTheDocumentedRoute() throws Exception {
        var item = new ShopItem(itemId, ShopCategory.AVATARS, "TEST", "Avatar", "", 80, true, false);
        when(shop.purchase(userId, ShopCategory.AVATARS, itemId)).thenReturn(new ShopService.Purchase(item, 20));

        mvc.perform(post("/api/shop/avatars/{id}/purchase", itemId).principal(principal)
                        .contentType("application/json").content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coinsBalance").value(20))
                .andExpect(jsonPath("$.item.owned").value(true));
        verify(shop).purchase(userId, ShopCategory.AVATARS, itemId);
    }

    @Test
    void mapsShopErrorsToTheSharedApiError() throws Exception {
        when(shop.purchase(userId, ShopCategory.SKILLS, itemId)).thenThrow(
                new ShopException("INSUFFICIENT_COINS", "Saldo de Coins insuficiente.", CONFLICT));

        mvc.perform(post("/api/shop/skills/{id}/purchase", itemId).principal(principal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_COINS"))
                .andExpect(jsonPath("$.path").value("/api/shop/skills/" + itemId + "/purchase"));
    }

    @Test
    void invalidCategoryReturns400WithoutCallingTheService() throws Exception {
        mvc.perform(get("/api/shop/invalid").principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHOP_INVALID_REQUEST"));
        verifyNoInteractions(shop);
    }
}
