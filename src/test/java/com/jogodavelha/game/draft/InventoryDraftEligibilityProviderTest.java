package com.jogodavelha.game.draft;

import com.jogodavelha.character.CharacterDTO;
import com.jogodavelha.character.CharacterService;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.skill.SkillDTO;
import com.jogodavelha.skill.SkillService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryDraftEligibilityProviderTest {
    private final UUID player = UUID.randomUUID();
    private final CharacterService characters = mock(CharacterService.class);
    private final SkillService skills = mock(SkillService.class);
    private final InventoryDraftEligibilityProvider provider = new InventoryDraftEligibilityProvider(characters, skills);

    @Test
    void usesOwnedItemsIncludingPurchasedSkillsWithoutFilteringInitialFlag() {
        UUID character = UUID.randomUUID();
        when(characters.getInventory(player)).thenReturn(List.of(
                new CharacterDTO(character, "Visual", CharacterSymbol.X, 0)));
        when(skills.getInventory(player)).thenReturn(List.of(
                skill("BOMBA", true, 0), skill("TORNADO", false, 100), skill("BOMBA", true, 0)));

        var eligible = provider.forPlayer(player);

        assertEquals(List.of(character), eligible.characters());
        assertEquals(List.of("BOMBA", "TORNADO"), eligible.skills());
        assertThrows(UnsupportedOperationException.class, () -> eligible.skills().add("ESCUDO"));
        verify(characters).getInventory(player);
        verify(skills).getInventory(player);
    }

    @Test
    void rejectsInventoryWithoutEnoughDistinctItems() {
        assertThrows(IllegalArgumentException.class,
                () -> new DraftEligibilityProvider.EligibleItems(List.of(), List.of("BOMBA", "ESCUDO")));
        assertThrows(IllegalArgumentException.class,
                () -> new DraftEligibilityProvider.EligibleItems(List.of(UUID.randomUUID()), List.of("BOMBA", "BOMBA")));
    }

    private SkillDTO skill(String code, boolean initial, int price) {
        return new SkillDTO(UUID.randomUUID(), code, code, "Descrição", initial, true, price);
    }
}
