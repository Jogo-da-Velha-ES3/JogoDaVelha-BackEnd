package com.jogodavelha.character;

import com.jogodavelha.auth.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CharacterServiceTest {

    private final UserCharacterRepository repository =
            mock(UserCharacterRepository.class);

    private final CharacterService service =
            new CharacterService(repository);

    @Test
    void returnsUserInventory() {

        UUID userId = UUID.randomUUID();

        Character character =
                new Character(
                        "X",
                        CharacterSymbol.X,
                        0
                );

        User user =
                new User(
                        "player",
                        "hash",
                        "player@test.com"
                );

        UserCharacter possession =
                new UserCharacter(user, character);

        when(repository.findByUser_Id(userId))
                .thenReturn(List.of(possession));

        List<CharacterDTO> inventory =
                service.getInventory(userId);

        assertEquals(1, inventory.size());

        assertEquals(
                CharacterSymbol.X,
                inventory.get(0).symbol()
        );
    }

    @Test
    void returnsEmptyInventoryWhenUserHasNoCharacters() {

        UUID userId = UUID.randomUUID();

        when(repository.findByUser_Id(userId))
                .thenReturn(List.of());

        List<CharacterDTO> inventory =
                service.getInventory(userId);

        assertTrue(inventory.isEmpty());
    }
}
