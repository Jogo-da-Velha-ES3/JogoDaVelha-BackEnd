package com.jogodavelha.shop;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.avatar.*;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.character.UserCharacter;
import com.jogodavelha.character.UserCharacterRepository;
import com.jogodavelha.coins.CoinTransactionService;
import com.jogodavelha.skill.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final UserCharacterRepository userCharacters = mock(UserCharacterRepository.class);
    private final SkillRepository skills = mock(SkillRepository.class);
    private final UserSkillRepository userSkills = mock(UserSkillRepository.class);
    private final AvatarRepository avatars = mock(AvatarRepository.class);
    private final UserAvatarRepository userAvatars = mock(UserAvatarRepository.class);
    private final UserEquipmentRepository equipment = mock(UserEquipmentRepository.class);
    private final CoinTransactionService transactions = mock(CoinTransactionService.class);
    private final ShopService shop = new ShopService(users, characters, userCharacters, skills, userSkills,
            avatars, userAvatars, equipment, transactions);
    private final UUID userId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final User user = new User("ana", "hash", "ana@example.com");
    private final com.jogodavelha.character.Character character = new com.jogodavelha.character.Character(
            "Estrela", CharacterSymbol.ESTRELA, 80);
    private final Skill skill = new Skill("Tornado", "TORNADO", SkillCategory.RELOCATION,
            "Move uma marca.", false, true, 80);
    private final Avatar avatar = new Avatar("TEST_AVATAR", "Avatar", "Avatar de teste", 80);

    @BeforeEach
    void setUp() {
        user.setId(userId);
        user.setCoinsBalance(100);
        ReflectionTestUtils.setField(character, "id", itemId);
        ReflectionTestUtils.setField(skill, "id", itemId);
        ReflectionTestUtils.setField(avatar, "id", itemId);
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(characters.findAll()).thenReturn(List.of(character));
        when(skills.findAll()).thenReturn(List.of(skill));
        when(avatars.findAll()).thenReturn(List.of(avatar));
    }

    @ParameterizedTest
    @EnumSource(ShopCategory.class)
    void purchaseDebitsAndRegistersOwnershipForEachCategory(ShopCategory category) {
        ShopService.Purchase purchase = shop.purchase(userId, category, itemId);

        assertEquals(20, purchase.coinsBalance());
        assertTrue(purchase.item().owned());
        verify(transactions).registerDebit(user, 80, "SHOP_" + category.name());
        switch (category) {
            case CHARACTERS -> verify(userCharacters).save(any(UserCharacter.class));
            case SKILLS -> verify(userSkills).save(any(UserSkill.class));
            case AVATARS -> verify(userAvatars).save(any(UserAvatar.class));
        }
    }

    @ParameterizedTest
    @EnumSource(ShopCategory.class)
    void insufficientBalanceDoesNotAcquireOrCharge(ShopCategory category) {
        user.setCoinsBalance(79);
        var error = assertThrows(ShopException.class, () -> shop.purchase(userId, category, itemId));

        assertEquals("INSUFFICIENT_COINS", error.getCode());
        assertEquals(79, user.getCoinsBalance());
        verify(userCharacters, never()).save(any());
        verify(userSkills, never()).save(any());
        verify(userAvatars, never()).save(any());
        verifyNoInteractions(transactions);
    }

    @Test
    void alreadyOwnedItemIsNotChargedAgain() {
        when(userCharacters.findByUser_Id(userId)).thenReturn(List.of(new UserCharacter(user, character)));

        assertEquals("SHOP_ITEM_ALREADY_OWNED", assertThrows(ShopException.class,
                () -> shop.purchase(userId, ShopCategory.CHARACTERS, itemId)).getCode());
        assertEquals(100, user.getCoinsBalance());
        verifyNoInteractions(transactions);
    }

    @Test
    void cannotEquipAnUnownedAvatarOrEquipASkill() {
        assertEquals("SHOP_ITEM_NOT_OWNED", assertThrows(ShopException.class,
                () -> shop.equip(userId, ShopCategory.AVATARS, itemId)).getCode());
        assertEquals("SHOP_INVALID_REQUEST", assertThrows(ShopException.class,
                () -> shop.equip(userId, ShopCategory.SKILLS, itemId)).getCode());
        verify(equipment, never()).save(any());
    }

    @Test
    void equippingAvatarPreservesTheEquippedCharacter() {
        UUID characterId = UUID.randomUUID();
        var selected = new UserEquipment(userId);
        selected.setCharacterId(characterId);
        when(equipment.findById(userId)).thenReturn(Optional.of(selected));
        when(userAvatars.findByUser_Id(userId)).thenReturn(List.of(new UserAvatar(user, avatar)));

        shop.equip(userId, ShopCategory.AVATARS, itemId);

        assertEquals(itemId, selected.getAvatarId());
        assertEquals(characterId, selected.getCharacterId());
        verifyNoInteractions(transactions);
    }

    @Test
    void defaultAvatarIsAvailableAndCanReplaceAPurchasedAvatarWithoutPurchase() {
        var fallback = new Avatar(Avatar.DEFAULT_CODE, "Padrão", "", 0);
        ReflectionTestUtils.setField(fallback, "id", itemId);
        when(avatars.findAll()).thenReturn(List.of(fallback));
        var selected = new UserEquipment(userId);
        selected.setAvatarId(UUID.randomUUID());
        when(equipment.findById(userId)).thenReturn(Optional.of(selected));

        assertTrue(shop.equip(userId, ShopCategory.AVATARS, itemId).equipped());
        assertNull(selected.getAvatarId());
        verifyNoInteractions(transactions);
    }

    @Test
    void unknownItemDoesNotChangeTheBalance() {
        assertEquals("SHOP_ITEM_NOT_FOUND", assertThrows(ShopException.class,
                () -> shop.purchase(userId, ShopCategory.AVATARS, UUID.randomUUID())).getCode());
        assertEquals(100, user.getCoinsBalance());
        verifyNoInteractions(transactions);
    }
}
