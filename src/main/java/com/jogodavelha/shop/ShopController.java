package com.jogodavelha.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
@Tag(name = "Shop", description = "Catálogo, compras e equipamento")
public class ShopController {
    private final ShopService shop;

    @GetMapping("/{category}")
    @Operation(summary = "Consultar catálogo", description = "Categorias: characters, skills e avatars.")
    public List<ShopItem> catalog(@PathVariable String category, Authentication auth) {
        return shop.catalog((UUID) auth.getPrincipal(), ShopCategory.parse(category));
    }

    @GetMapping("/{category}/inventory")
    @Operation(summary = "Consultar itens próprios de uma categoria")
    public List<ShopItem> inventory(@PathVariable String category, Authentication auth) {
        return shop.catalog((UUID) auth.getPrincipal(), ShopCategory.parse(category)).stream()
                .filter(ShopItem::owned).toList();
    }

    @PostMapping("/{category}/{itemId}/purchase")
    @Operation(summary = "Comprar item com Coins", description = "Saldo, posse e extrato são atualizados na mesma transação.")
    public ShopService.Purchase purchase(@PathVariable String category, @PathVariable UUID itemId, Authentication auth) {
        return shop.purchase((UUID) auth.getPrincipal(), ShopCategory.parse(category), itemId);
    }

    @PutMapping("/{category}/{itemId}/equip")
    @Operation(summary = "Equipar personagem ou avatar", description = "Não altera o personagem do Draft nem as habilidades escolhidas.")
    public ShopItem equip(@PathVariable String category, @PathVariable UUID itemId, Authentication auth) {
        return shop.equip((UUID) auth.getPrincipal(), ShopCategory.parse(category), itemId);
    }
}
