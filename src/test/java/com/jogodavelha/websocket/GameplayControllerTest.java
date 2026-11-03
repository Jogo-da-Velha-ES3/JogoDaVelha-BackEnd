package com.jogodavelha.websocket;

import com.jogodavelha.game.service.MoveActionService;
import com.jogodavelha.game.service.GameplayRoomResolver;
import com.jogodavelha.skill.SkillActivationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameplayControllerTest {
    @Test void principalIsUsedToIdentifyThePlayer() {
        MoveActionService moves = mock(MoveActionService.class);
        GameplayRoomResolver resolver = mock(GameplayRoomResolver.class);
        GameplayController controller = new GameplayController(moves, mock(SkillActivationService.class), resolver,
                mock(com.jogodavelha.game.service.GameLifecycleService.class));
        UUID game = UUID.randomUUID(), authenticated = UUID.randomUUID();
        when(resolver.resolveActiveGame("0042", authenticated)).thenReturn(game);
        controller.move("0042", new GameplayController.MoveRequest(3), () -> authenticated.toString());
        verify(resolver).resolveActiveGame("0042", authenticated);
        verify(moves).play(game, authenticated, 3);
    }

    @Test void missingPrincipalIsForbidden() {
        GameplayController controller = new GameplayController(mock(MoveActionService.class), mock(SkillActivationService.class),
                mock(GameplayRoomResolver.class), mock(com.jogodavelha.game.service.GameLifecycleService.class));
        assertThrows(AccessDeniedException.class, () -> controller.move("0042",
                new GameplayController.MoveRequest(1), null));
    }

    @Test void skillBuildsContextFromPrincipal() {
        SkillActivationService skills = mock(SkillActivationService.class);
        GameplayRoomResolver resolver = mock(GameplayRoomResolver.class);
        GameplayController controller = new GameplayController(mock(MoveActionService.class), skills, resolver,
                mock(com.jogodavelha.game.service.GameLifecycleService.class));
        UUID game = UUID.randomUUID(), authenticated = UUID.randomUUID();
        when(resolver.resolveActiveGame("0042", authenticated)).thenReturn(game);
        controller.activateSkill("0042", new GameplayController.SkillRequest("BOMBA", 2, null),
                () -> authenticated.toString());
        verify(resolver).resolveActiveGame("0042", authenticated);
        verify(skills).activate(eq(game), eq("BOMBA"), argThat(context -> context.casterId().equals(authenticated)
                && context.targetPosition() == 2 && context.destinationPosition() == null));
    }

    @Test
    void gameplayUsesOnlyRoomCodeDestinationsAndKeepsBackendPayloadShapes() throws Exception {
        assertArrayEquals(new String[]{"/rooms/{code}/move"}, GameplayController.class
                .getMethod("move", String.class, GameplayController.MoveRequest.class, java.security.Principal.class)
                .getAnnotation(MessageMapping.class).value());
        assertArrayEquals(new String[]{"/rooms/{code}/ability"}, GameplayController.class
                .getMethod("activateSkill", String.class, GameplayController.SkillRequest.class, java.security.Principal.class)
                .getAnnotation(MessageMapping.class).value());
        assertEquals(List.of("position"), java.util.Arrays.stream(GameplayController.MoveRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList());
        assertEquals(List.of("skillCode", "targetPosition", "destinationPosition"),
                java.util.Arrays.stream(GameplayController.SkillRequest.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void leaveUsesAuthenticatedPlayerAndRoomCode() throws Exception {
        GameplayRoomResolver resolver = mock(GameplayRoomResolver.class);
        com.jogodavelha.game.service.GameLifecycleService lifecycle =
                mock(com.jogodavelha.game.service.GameLifecycleService.class);
        GameplayController controller = new GameplayController(mock(MoveActionService.class),
                mock(SkillActivationService.class), resolver, lifecycle);
        UUID actor = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        when(resolver.resolveActiveGame("0042", actor)).thenReturn(game);

        controller.leave("0042", actor::toString);

        verify(resolver).resolveActiveGame("0042", actor);
        verify(lifecycle).forfeitActiveGame(game, actor);
        assertArrayEquals(new String[]{"/rooms/{code}/leave"}, GameplayController.class
                .getMethod("leave", String.class, Principal.class).getAnnotation(MessageMapping.class).value());
    }
}
