package com.jogodavelha.skill;

import com.jogodavelha.auth.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillServiceTest {

    private final UserSkillRepository repository =
            mock(UserSkillRepository.class);

    private final SkillService service =
            new SkillService(repository);

    @Test
    void returnsUserInventory() {

        UUID userId = UUID.randomUUID();

        Skill skill =
                new Skill(
                        "Bomba",
                        "BOMBA",
                        SkillCategory.REMOVAL,
                        "Remove uma peça do oponente.",
                        true,
                        true,
                        0
                );

        User user =
                new User(
                        "player",
                        "hash",
                        "player@test.com"
                );

        UserSkill ownership =
                new UserSkill(user, skill);

        when(repository.findByUser_Id(userId))
                .thenReturn(List.of(ownership));

        List<SkillDTO> inventory =
                service.getInventory(userId);

        assertEquals(1, inventory.size());

        assertEquals(
                "BOMBA",
                inventory.get(0).code()
        );
    }

    @Test
    void returnsEmptyInventoryWhenUserHasNoSkills() {

        UUID userId = UUID.randomUUID();

        when(repository.findByUser_Id(userId))
                .thenReturn(List.of());

        List<SkillDTO> inventory =
                service.getInventory(userId);

        assertTrue(inventory.isEmpty());
    }
}