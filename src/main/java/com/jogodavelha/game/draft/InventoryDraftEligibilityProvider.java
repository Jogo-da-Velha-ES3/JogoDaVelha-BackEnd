package com.jogodavelha.game.draft;

import com.jogodavelha.character.CharacterDTO;
import com.jogodavelha.character.CharacterService;
import com.jogodavelha.skill.SkillDTO;
import com.jogodavelha.skill.SkillService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class InventoryDraftEligibilityProvider implements DraftEligibilityProvider {
    private final CharacterService characters;
    private final SkillService skills;

    public InventoryDraftEligibilityProvider(CharacterService characters, SkillService skills) {
        this.characters = characters;
        this.skills = skills;
    }

    @Override
    @Transactional(readOnly = true)
    public EligibleItems forPlayer(UUID playerId) {
        return new EligibleItems(characters.getInventory(playerId).stream().map(CharacterDTO::id).toList(),
                skills.getInventory(playerId).stream().map(SkillDTO::code).toList());
    }
}
