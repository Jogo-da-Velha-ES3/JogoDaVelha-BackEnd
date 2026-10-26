package com.jogodavelha.game.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftAbilitiesControllerTest {
    private final DraftAbilitiesService abilities = mock(DraftAbilitiesService.class);
    private final DraftAbilitiesController controller = new DraftAbilitiesController(abilities);

    @Test
    void usesPrincipalAndIgnoresIdentityFromPayload() throws Exception {
        UUID authenticated = UUID.randomUUID();
        String json = "{\"abilities\":[\"BOMBA\",\"ESCUDO\"],\"playerId\":\"" + UUID.randomUUID() + "\"}";
        var request = new ObjectMapper().readValue(json, DraftAbilitiesController.Request.class);

        controller.confirm("0042", request, () -> authenticated.toString());

        verify(abilities).confirm("0042", authenticated, List.of("BOMBA", "ESCUDO"));
    }

    @Test
    void rejectsMissingOrInvalidIdentityBeforeCallingService() {
        var request = new DraftAbilitiesController.Request(List.of("BOMBA", "ESCUDO"));
        for (Principal principal : new Principal[]{null, () -> null, () -> "invalid"}) {
            assertThrows(AccessDeniedException.class, () -> controller.confirm("0042", request, principal));
        }
        verifyNoInteractions(abilities);
    }

    @Test
    void rejectsMissingRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.confirm("0042", null, () -> UUID.randomUUID().toString()));
        verifyNoInteractions(abilities);
    }
}
