package com.jogodavelha.game.service;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomNotFoundException;
import com.jogodavelha.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameplayRoomResolverTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final GameplayRoomResolver resolver = new GameplayRoomResolver(rooms, games);
    private final UUID roomId = UUID.randomUUID();
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final Room room = mock(Room.class);
    private final Game game = mock(Game.class);

    @BeforeEach
    void setUp() {
        when(room.getId()).thenReturn(roomId);
        User roomPlayer1 = user(p1);
        User roomPlayer2 = user(p2);
        when(room.getPlayer1()).thenReturn(roomPlayer1);
        when(room.getPlayer2()).thenReturn(roomPlayer2);
        when(rooms.findByCodeAndStatusIn(eq("0042"), anyCollection())).thenReturn(Optional.of(room));
        User gamePlayer1 = user(p1);
        User gamePlayer2 = user(p2);
        when(game.getId()).thenReturn(gameId);
        when(game.getRoom()).thenReturn(room);
        when(game.getPlayer1()).thenReturn(gamePlayer1);
        when(game.getPlayer2()).thenReturn(gamePlayer2);
        when(games.findByRoomIdAndStatus(roomId, GameStatus.IN_PROGRESS)).thenReturn(Optional.of(game));
    }

    @Test
    void resolvesTheActiveGameFromRoomCodeForEitherMember() {
        assertEquals(gameId, resolver.resolveActiveGame("0042", p1));
        assertEquals(gameId, resolver.resolveActiveGame("0042", p2));
        verify(games, times(2)).findByRoomIdAndStatus(roomId, GameStatus.IN_PROGRESS);
    }

    @Test
    void rejectsNonMembersBeforeResolvingTheGame() {
        assertThrows(AccessDeniedException.class, () -> resolver.resolveActiveGame("0042", outsider));
        verifyNoInteractions(games);
    }

    @Test
    void rejectsUnknownRoomsAndRoomsWithoutAnActiveGame() {
        when(rooms.findByCodeAndStatusIn(eq("9999"), anyCollection())).thenReturn(Optional.empty());
        assertThrows(RoomNotFoundException.class, () -> resolver.resolveActiveGame("9999", p1));

        when(games.findByRoomIdAndStatus(roomId, GameStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> resolver.resolveActiveGame("0042", p1));
    }

    @Test
    void rejectsGamesThatDoNotMatchTheRoomOrItsPlayers() {
        Room otherRoom = mock(Room.class);
        when(otherRoom.getId()).thenReturn(UUID.randomUUID());
        when(game.getRoom()).thenReturn(otherRoom);
        assertThrows(AccessDeniedException.class, () -> resolver.resolveActiveGame("0042", p1));

        when(game.getRoom()).thenReturn(room);
        User otherPlayer1 = user(outsider);
        User otherPlayer2 = user(outsider);
        when(game.getPlayer1()).thenReturn(otherPlayer1);
        when(game.getPlayer2()).thenReturn(otherPlayer2);
        assertThrows(AccessDeniedException.class, () -> resolver.resolveActiveGame("0042", p1));
    }

    @Test
    void validatesRoomCodeAndAuthenticatedIdentity() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveActiveGame("42", p1));
        assertThrows(AccessDeniedException.class, () -> resolver.resolveActiveGame("0042", null));
        verifyNoInteractions(rooms, games);
    }

    private User user(UUID id) {
        User user = new User("player", "hash", id + "@example.test");
        user.setId(id);
        return user;
    }
}
