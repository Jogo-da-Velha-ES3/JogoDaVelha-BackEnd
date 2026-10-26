package com.jogodavelha.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.User;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomPlayerService;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomStatus;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomSnapshotServiceTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final DraftStateManager drafts = mock(DraftStateManager.class);
    private final GameStateManager gameStates = mock(GameStateManager.class);
    private final RoomPlayerService players = spy(new RoomPlayerService());
    private final WebSocketService websocket = mock(WebSocketService.class);
    private final RoomSnapshotService snapshots = new RoomSnapshotService(rooms, games, drafts, gameStates, players, websocket);

    @Test
    void composesRoomPlayersThroughSharedPlayerServiceAndOmitsUnavailableBlocks() {
        UUID roomId = UUID.randomUUID();
        User host = new User("host", "password", "host@example.com");
        host.setId(UUID.randomUUID());
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getCode()).thenReturn("0042");
        when(room.getStatus()).thenReturn(RoomStatus.WAITING);
        when(room.getPlayer1()).thenReturn(host);
        when(rooms.findByCodeAndStatusIn("0042", List.of(RoomStatus.WAITING, RoomStatus.IN_GAME)))
                .thenReturn(Optional.of(room));
        when(drafts.find(roomId)).thenReturn(Optional.empty());

        var result = snapshots.compose("0042");

        assertEquals("0042", result.room().code());
        assertEquals(host.getId(), result.room().player1().id());
        assertEquals("host", result.room().player1().username());
        assertNull(result.room().player2());
        assertNull(result.draft());
        assertNull(result.match());
        assertNull(result.rematch());
        verify(players).host(room);
        verify(games).findByRoomIdAndStatus(roomId, com.jogodavelha.game.GameStatus.IN_PROGRESS);
        verify(games).findByRoomIdAndStatus(roomId, com.jogodavelha.game.GameStatus.FINISHED);
        verifyNoInteractions(gameStates);
    }

    @Test
    void publishesRoomSnapshotOnCanonicalTopicWithStandardEnvelope() throws Exception {
        UUID roomId = UUID.randomUUID();
        Room room = mock(Room.class);
        User host = new User("host", "password", "host@example.com");
        host.setId(UUID.randomUUID());
        when(room.getId()).thenReturn(roomId);
        when(room.getCode()).thenReturn("0042");
        when(room.getStatus()).thenReturn(RoomStatus.WAITING);
        when(room.getPlayer1()).thenReturn(host);
        when(rooms.findByCodeAndStatusIn("0042", List.of(RoomStatus.WAITING, RoomStatus.IN_GAME)))
                .thenReturn(Optional.of(room));
        when(drafts.find(roomId)).thenReturn(Optional.empty());

        snapshots.publish("0042");

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), captor.capture());
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(captor.getValue());
        assertEquals("ROOM_SNAPSHOT", json.path("type").asText());
        assertTrue(json.has("serverTime"));
        assertEquals("0042", json.path("payload").path("room").path("code").asText());
        assertFalse(json.toString().contains("password"));
    }

    @Test
    void syncPublishesOnlyAfterAuthenticatedParticipantIsValidated() {
        UUID roomId = UUID.randomUUID();
        User host = new User("host", "password", "host@example.com");
        host.setId(UUID.randomUUID());
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getCode()).thenReturn("0042");
        when(room.getStatus()).thenReturn(RoomStatus.WAITING);
        when(room.getPlayer1()).thenReturn(host);
        when(rooms.findByCodeAndStatusIn("0042", List.of(RoomStatus.WAITING, RoomStatus.IN_GAME)))
                .thenReturn(Optional.of(room));
        when(drafts.find(roomId)).thenReturn(Optional.empty());

        snapshots.sync("0042", host.getId());

        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), any());
    }

    @Test
    void syncRejectsNonParticipantWithoutPublishing() {
        UUID roomId = UUID.randomUUID();
        User host = new User("host", "password", "host@example.com");
        host.setId(UUID.randomUUID());
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getPlayer1()).thenReturn(host);
        when(room.getPlayer2()).thenReturn(null);
        when(rooms.findByCodeAndStatusIn("0042", List.of(RoomStatus.WAITING, RoomStatus.IN_GAME)))
                .thenReturn(Optional.of(room));

        assertThrows(AccessDeniedException.class, () -> snapshots.sync("0042", UUID.randomUUID()));

        verifyNoInteractions(websocket, games, drafts, gameStates);
    }
}
