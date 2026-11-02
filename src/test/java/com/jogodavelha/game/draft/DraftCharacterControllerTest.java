package com.jogodavelha.game.draft;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DraftCharacterControllerTest {
    private final DraftCharacterService service = mock(DraftCharacterService.class);
    private final DraftCharacterController controller = new DraftCharacterController(service);

    @Test
    void rejectsMissingPrincipal() {
        assertThrows(AccessDeniedException.class,
                () -> controller.select("0042", new DraftCharacterController.Request("x"), null));
    }

    @Test
    void rejectsInvalidPrincipalName() {
        Principal principal = () -> "nao-e-uuid";
        assertThrows(AccessDeniedException.class,
                () -> controller.select("0042", new DraftCharacterController.Request("x"), principal));
    }

    @Test
    void rejectsNullBody() {
        Principal principal = () -> UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class, () -> controller.select("0042", null, principal));
    }

    @Test
    void delegatesToService() {
        UUID playerId = UUID.randomUUID();
        Principal principal = playerId::toString;

        controller.select("0042", new DraftCharacterController.Request("abc"), principal);

        verify(service).select("0042", playerId, "abc");
    }
}