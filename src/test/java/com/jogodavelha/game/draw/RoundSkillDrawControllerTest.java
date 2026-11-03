package com.jogodavelha.game.draw;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RoundSkillDrawControllerTest {
    private final RoundSkillDrawService draws = mock(RoundSkillDrawService.class);
    private final RoundSkillDrawController controller = new RoundSkillDrawController(draws);

    @Test
    void usesAuthenticatedIdentityAndIgnoresPlayerIdInThePayload() throws Exception {
        UUID actor = UUID.randomUUID();
        var request = new ObjectMapper().readValue("{\"ability\":\"TORNADO\",\"abilityToRemove\":\"ESCUDO\",\"playerId\":\""
                + UUID.randomUUID() + "\"}", RoundSkillDrawController.Request.class);
        controller.pick("0042", request, () -> actor.toString());
        verify(draws).pick("0042", actor, "TORNADO", "ESCUDO");
    }

    @Test
    void rejectsMissingIdentityInvalidIdentityAndMissingRequest() {
        var request = new RoundSkillDrawController.Request("TORNADO", null);
        assertThrows(AccessDeniedException.class, () -> controller.pick("0042", request, null));
        assertThrows(AccessDeniedException.class, () -> controller.pick("0042", request, () -> "bad"));
        assertThrows(IllegalArgumentException.class, () -> controller.pick("0042", null, () -> UUID.randomUUID().toString()));
        verifyNoInteractions(draws);
    }
}
