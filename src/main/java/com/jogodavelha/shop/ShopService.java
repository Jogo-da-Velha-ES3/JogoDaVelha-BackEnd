package com.jogodavelha.shop;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.avatar.*;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.UserCharacter;
import com.jogodavelha.character.UserCharacterRepository;
import com.jogodavelha.coins.CoinTransactionService;
import com.jogodavelha.skill.SkillRepository;
import com.jogodavelha.skill.UserSkill;
import com.jogodavelha.skill.UserSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {
    private final UserRepository users;
    private final CharacterRepository characters;
    private final UserCharacterRepository userCharacters;
    private final SkillRepository skills;
    private final UserSkillRepository userSkills;
    private final AvatarRepository avatars;
    private final UserAvatarRepository userAvatars;
    private final UserEquipmentRepository equipment;
    private final CoinTransactionService transactions;

    public record Purchase(ShopItem item, int coinsBalance) { }

    public List<ShopItem> catalog(UUID userId, ShopCategory category) {
        UserEquipment equipped = equipment.findById(userId).orElseGet(() -> new UserEquipment(userId));
        Set<UUID> owned = switch (category) {
            case CHARACTERS -> userCharacters.findByUser_Id(userId).stream()
                    .map(item -> item.getCharacter().getId()).collect(Collectors.toSet());
            case SKILLS -> userSkills.findByUser_Id(userId).stream()
                    .map(item -> item.getSkill().getId()).collect(Collectors.toSet());
            case AVATARS -> userAvatars.findByUser_Id(userId).stream()
                    .map(item -> item.getAvatar().getId()).collect(Collectors.toSet());
        };
        List<ShopItem> items = switch (category) {
            case CHARACTERS -> characters.findAll().stream().map(item -> new ShopItem(item.getId(), category,
                    item.getSymbol().name(), item.getName(), "Personagem cosmético para o Draft.", item.getPriceCoins(),
                    owned.contains(item.getId()), item.getId().equals(equipped.getCharacterId()))).toList();
            case SKILLS -> skills.findAll().stream().map(item -> new ShopItem(item.getId(), category,
                    item.getCode(), item.getName(), item.getDescription(), item.getPriceCoins(),
                    owned.contains(item.getId()) || Boolean.TRUE.equals(item.getInitialSkill()), false)).toList();
            case AVATARS -> avatars.findAll().stream().map(item -> {
                boolean defaultAvatar = Avatar.DEFAULT_CODE.equals(item.getCode());
                return new ShopItem(item.getId(), category, item.getCode(), item.getName(), item.getDescription(),
                        item.getPriceCoins(), defaultAvatar || owned.contains(item.getId()),
                        item.getId().equals(equipped.getAvatarId()) || defaultAvatar && equipped.getAvatarId() == null);
            }).toList();
        };
        return items.stream().sorted(Comparator.comparing(ShopItem::priceCoins).thenComparing(ShopItem::code)).toList();
    }

    @Transactional
    public Purchase purchase(UUID userId, ShopCategory category, UUID itemId) {
        // Serializa compras/equipamentos do mesmo usuário, inclusive na primeira aquisição.
        User user = lockedUser(userId);
        ShopItem item = item(userId, category, itemId);
        if (item.owned()) throw new ShopException("SHOP_ITEM_ALREADY_OWNED", "Você já possui este item.", CONFLICT);
        if (user.getCoinsBalance() < item.priceCoins()) {
            throw new ShopException("INSUFFICIENT_COINS", "Saldo de Coins insuficiente.", CONFLICT);
        }
        switch (category) {
            case CHARACTERS -> userCharacters.save(new UserCharacter(user, characters.getReferenceById(itemId)));
            case SKILLS -> userSkills.save(new UserSkill(user, skills.getReferenceById(itemId)));
            case AVATARS -> userAvatars.save(new UserAvatar(user, avatars.getReferenceById(itemId)));
        }
        user.setCoinsBalance(user.getCoinsBalance() - item.priceCoins());
        if (item.priceCoins() > 0) transactions.registerDebit(user, item.priceCoins(), "SHOP_" + category.name());
        return new Purchase(new ShopItem(item.id(), category, item.code(), item.name(), item.description(),
                item.priceCoins(), true, item.equipped()), user.getCoinsBalance());
    }

    @Transactional
    public ShopItem equip(UUID userId, ShopCategory category, UUID itemId) {
        lockedUser(userId);
        if (category == ShopCategory.SKILLS) {
            throw new ShopException("SHOP_INVALID_REQUEST", "Habilidades são selecionadas no Draft.", BAD_REQUEST);
        }
        ShopItem item = item(userId, category, itemId);
        if (!item.owned()) throw new ShopException("SHOP_ITEM_NOT_OWNED", "Você não possui este item.", FORBIDDEN);
        UserEquipment selected = equipment.findById(userId).orElseGet(() -> new UserEquipment(userId));
        if (category == ShopCategory.CHARACTERS) selected.setCharacterId(itemId);
        else selected.setAvatarId(Avatar.DEFAULT_CODE.equals(item.code()) ? null : itemId);
        equipment.save(selected);
        return new ShopItem(item.id(), category, item.code(), item.name(), item.description(), item.priceCoins(), true, true);
    }

    private User lockedUser(UUID userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ShopException("NOT_FOUND", "Usuário não encontrado.", NOT_FOUND));
    }

    private ShopItem item(UUID userId, ShopCategory category, UUID itemId) {
        return catalog(userId, category).stream().filter(item -> item.id().equals(itemId)).findFirst()
                .orElseThrow(() -> new ShopException("SHOP_ITEM_NOT_FOUND", "Item não encontrado nesta categoria.", NOT_FOUND));
    }
}
