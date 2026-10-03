package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomServiceTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final RoomCodeAllocator allocator = spy(new RoomCodeAllocator(rooms, games, mock(JdbcTemplate.class)));
    private final RoomService service = new RoomService(rooms, users, allocator);
    private final User owner = player("owner");
    private final User guest = player("guest");
    private final Room room = new Room("0042", owner);

    @Test
    void createsWaitingRoomForExistingPlayer() {
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(rooms.existsByCodeAndStatusIn(anyString(), anyCollection())).thenReturn(true, false);
        when(rooms.saveAndFlush(any(Room.class))).thenAnswer(call -> call.getArgument(0));

        Room created = service.createRoom(owner.getId());

        assertTrue(created.getCode().matches("[0-9]{4}"));
        assertSame(owner, created.getPlayer1());
        assertEquals(RoomStatus.WAITING, created.getStatus());
        assertFalse(created.isReadyToStart());
        verify(rooms, times(2)).existsByCodeAndStatusIn(anyString(), anyCollection());
    }

    @Test
    void secondPlayerMakesRoomReadyAndStartsGame() {
        prepareJoin();
        when(rooms.saveAndFlush(room)).thenReturn(room);

        assertSame(room, service.joinRoom("0042", guest.getId()));
        assertSame(guest, room.getPlayer2());
        assertTrue(room.isReadyToStart());
        assertEquals(RoomStatus.IN_GAME, room.getStatus());
        var order = inOrder(allocator, rooms);
        order.verify(allocator).lockMembership();
        order.verify(rooms).findByCodeAndStatusIn("0042", RoomCodeAllocator.ACTIVE);
        order.verify(rooms).saveAndFlush(room);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"42", "12345", "abcd", "１２３４", " 0042"})
    void rejectsInvalidCodeBeforeAccessingDatabase(String code) {
        assertThrows(IllegalArgumentException.class, () -> service.joinRoom(code, guest.getId()));
        verifyNoInteractions(users, rooms, allocator);
    }

    @Test
    void rejectsMissingOrClosedRoom() {
        when(users.findById(guest.getId())).thenReturn(Optional.of(guest));
        assertThrows(IllegalArgumentException.class, () -> service.joinRoom("0042", guest.getId()));
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStartedRoom() {
        prepareJoin();
        room.setStatus(RoomStatus.IN_GAME);
        assertJoinRejected("A partida desta sala já foi iniciada.");
    }

    @Test
    void rejectsFullRoomWithoutReplacingSecondPlayer() {
        prepareJoin();
        User second = player("second");
        room.join(second);
        assertJoinRejected("A partida desta sala já foi iniciada.");
        assertSame(second, room.getPlayer2());
    }

    @Test
    void rejectsThirdPlayerWhenRoomIsInGame() {
        prepareJoin();
        User second = player("second");
        User third = player("third");
        room.join(second);
        room.setStatus(RoomStatus.IN_GAME);
        when(users.findById(third.getId())).thenReturn(Optional.of(third));
        var error = assertThrows(IllegalStateException.class, () -> service.joinRoom("0042", third.getId()));
        assertEquals("A partida desta sala já foi iniciada.", error.getMessage());
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOwnerJoiningAsSecondPlayer() {
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(rooms.findByCodeAndStatusIn("0042", RoomCodeAllocator.ACTIVE)).thenReturn(Optional.of(room));
        assertThrows(IllegalStateException.class, () -> service.joinRoom("0042", owner.getId()));
        assertNull(room.getPlayer2());
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPlayerAlreadyInAnotherRoom() {
        prepareJoin();
        when(rooms.existsByStatusInAndPlayer1IdOrStatusInAndPlayer2Id(
                RoomCodeAllocator.ACTIVE, guest.getId(), RoomCodeAllocator.ACTIVE, guest.getId())).thenReturn(true);
        assertJoinRejected("Jogador já está em uma sala ativa");
        assertNull(room.getPlayer2());
    }

    @Test
    void rejectsPlayerInActiveGame() {
        prepareJoin();
        when(games.findActiveGameByPlayerId(guest.getId())).thenReturn(Optional.of(mock(Game.class)));
        assertJoinRejected("Jogador já está em uma partida ativa");
        assertNull(room.getPlayer2());
    }

    @Test
    void rejectsMissingPlayer() {
        assertThrows(IllegalArgumentException.class, () -> service.createRoom(null));
        assertThrows(IllegalArgumentException.class, () -> service.createRoom(guest.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.joinRoom("0042", null));
        assertThrows(IllegalArgumentException.class, () -> service.joinRoom("0042", guest.getId()));
        verifyNoInteractions(allocator, rooms);
    }

    private void prepareJoin() {
        when(users.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(rooms.findByCodeAndStatusIn("0042", RoomCodeAllocator.ACTIVE)).thenReturn(Optional.of(room));
    }

    private void assertJoinRejected(String message) {
        var error = assertThrows(IllegalStateException.class, () -> service.joinRoom("0042", guest.getId()));
        assertEquals(message, error.getMessage());
        verify(rooms, never()).saveAndFlush(any());
    }

    private static User player(String name) {
        User user = new User(name, "hash", name + "@example.test");
        user.setId(UUID.randomUUID());
        return user;
    }
}
