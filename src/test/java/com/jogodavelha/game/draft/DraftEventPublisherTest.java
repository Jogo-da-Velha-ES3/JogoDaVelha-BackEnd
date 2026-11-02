package com.jogodavelha.game.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.websocket.WebSocketService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftEventPublisherTest {
    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final WebSocketService websocket = mock(WebSocketService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DraftEventPublisher publisher = new DraftEventPublisher(
            websocket,
            characters,
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
            users
    );

    @Test
    void publishesCharacterSymbolsAndReadinessWithoutPrivateChoices() throws Exception {
        UUID x = UUID.randomUUID(), o = UUID.randomUUID();
        Character first = new Character("X", CharacterSymbol.X, 0);
        first.setId(x);
        Character second = new Character("O", CharacterSymbol.O, 0);
        second.setId(o);
        when(characters.findAllById(List.of(x, o))).thenReturn(List.of(second, first));
        var items = new DraftEligibilityProvider.EligibleItems(List.of(x, o), List.of("BOMBA", "ESCUDO"));
        var state = new DraftState(UUID.randomUUID(), "0042", 21000, 3,
                DraftState.Player.initial(UUID.randomUUID(), items).withCharacter(x).withSkills(items.skills()),
                DraftState.Player.initial(UUID.randomUUID(), items).withCharacter(o));

        publisher.onDraftUpdated(DraftUpdated.from(state));

        var captor = ArgumentCaptor.forClass(DraftEventPublisher.Message.class);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), captor.capture());
        var json = new ObjectMapper().valueToTree(captor.getValue());
        assertEquals("DRAFT_UPDATE", json.path("type").asText());
        assertEquals(1000, json.path("serverTime").asLong());
        assertEquals(21000, json.path("payload").path("deadline").asLong());
        assertEquals("X", json.path("payload").path("characters").path("P1").asText());
        assertEquals("O", json.path("payload").path("characters").path("P2").asText());
        assertTrue(json.path("payload").path("ready").path("P1").asBoolean());
        assertFalse(json.path("payload").path("ready").path("P2").asBoolean());
        assertEquals(4, json.path("payload").size());
        assertFalse(json.toString().contains("BOMBA"));
        assertFalse(json.toString().contains("eligible"));
        assertFalse(json.toString().contains(x.toString()));
    }

    @Test
    void emptySelectionDoesNotQueryTheCatalog() {
        var items = new DraftEligibilityProvider.EligibleItems(List.of(UUID.randomUUID(), UUID.randomUUID()),
                List.of("BOMBA", "ESCUDO"));
        var state = new DraftState(UUID.randomUUID(), "0042", 21000, 0,
                DraftState.Player.initial(UUID.randomUUID(), items), DraftState.Player.initial(UUID.randomUUID(), items));

        publisher.onDraftUpdated(DraftUpdated.from(state));

        verifyNoInteractions(characters);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), argThat(message ->
                ((DraftEventPublisher.Message) message).payload().characters().isEmpty()));
    }

    @Test
    void nicknamesContainOnlyPlayersWhoChoseACharacter() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), characterId = UUID.randomUUID();
        var character = mock(com.jogodavelha.character.Character.class);
        when(character.getId()).thenReturn(characterId);
        when(character.getSymbol()).thenReturn(com.jogodavelha.character.CharacterSymbol.X);
        when(characters.findAllById(any())).thenReturn(List.of(character));
        when(users.findById(p1)).thenReturn(Optional.of(new com.jogodavelha.auth.User("ana", "h", "a@a.com")));

        publisher.onDraftUpdated(new DraftUpdated(UUID.randomUUID(), "0042", 1L, 1L, List.of(
                new DraftUpdated.PlayerStatus(p1, characterId, DraftState.Stage.SKILLS, false),
                new DraftUpdated.PlayerStatus(p2, null, DraftState.Stage.CHARACTER, false))));

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), captor.capture());
        var message = (DraftEventPublisher.Message) captor.getValue();
        assertEquals(java.util.Map.of("P1", "ana"), message.payload().nicknames());
    }

    @Test
    void nicknameLookupFailureDoesNotPreventDraftUpdatePublication() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        UUID characterX = UUID.randomUUID(), characterO = UUID.randomUUID();
        var firstCharacter = mock(com.jogodavelha.character.Character.class);
        when(firstCharacter.getId()).thenReturn(characterX);
        when(firstCharacter.getSymbol()).thenReturn(CharacterSymbol.X);
        var secondCharacter = mock(com.jogodavelha.character.Character.class);
        when(secondCharacter.getId()).thenReturn(characterO);
        when(secondCharacter.getSymbol()).thenReturn(CharacterSymbol.O);
        when(characters.findAllById(any())).thenReturn(List.of(firstCharacter, secondCharacter));
        when(users.findById(p1)).thenThrow(new IllegalStateException("database unavailable"));
        when(users.findById(p2)).thenReturn(
                Optional.of(new com.jogodavelha.auth.User("bia", "h", "bia@example.com")));

        publisher.onDraftUpdated(new DraftUpdated(UUID.randomUUID(), "0042", 21_000L, 2L, List.of(
                new DraftUpdated.PlayerStatus(p1, characterX, DraftState.Stage.SKILLS, false),
                new DraftUpdated.PlayerStatus(p2, characterO, DraftState.Stage.SKILLS, false))));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), captor.capture());
        var message = (DraftEventPublisher.Message) captor.getValue();
        assertEquals("DRAFT_UPDATE", message.type());
        assertEquals(java.util.Map.of("P1", CharacterSymbol.X, "P2", CharacterSymbol.O),
                message.payload().characters());
        assertEquals(java.util.Map.of("P1", false, "P2", false), message.payload().ready());
        assertEquals(java.util.Map.of("P2", "bia"), message.payload().nicknames());
    }
}
