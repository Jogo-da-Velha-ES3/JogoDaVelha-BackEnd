package com.jogodavelha.websocket;

import com.jogodavelha.game.service.MoveActionService;
import com.jogodavelha.skill.SkillActivationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameplayControllerTest {
    @Test void principalIsUsedAndPayloadIdentityIsIgnored() {
        MoveActionService moves = mock(MoveActionService.class);
        GameplayController controller = new GameplayController(moves, mock(SkillActivationService.class));
        UUID game = UUID.randomUUID(), authenticated = UUID.randomUUID();
        controller.move(game, new GameplayController.MoveRequest(3, UUID.randomUUID()), () -> authenticated.toString());
        verify(moves).play(game, authenticated, 3);
    }

    @Test void missingPrincipalIsForbidden() {
        GameplayController controller = new GameplayController(mock(MoveActionService.class), mock(SkillActivationService.class));
        assertThrows(AccessDeniedException.class, () -> controller.move(UUID.randomUUID(),
                new GameplayController.MoveRequest(1, UUID.randomUUID()), null));
    }

    @Test void skillBuildsContextFromPrincipal() {
        SkillActivationService skills = mock(SkillActivationService.class);
        GameplayController controller = new GameplayController(mock(MoveActionService.class), skills);
        UUID game = UUID.randomUUID(), authenticated = UUID.randomUUID();
        controller.activateSkill(game, new GameplayController.SkillRequest("BOMBA", 2, null, UUID.randomUUID()),
                () -> authenticated.toString());
        verify(skills).activate(eq(game), eq("BOMBA"), argThat(context -> context.casterId().equals(authenticated)
                && context.targetPosition() == 2));
    }
}
