package com.jogodavelha.game.service;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.draft.DraftEligibilityProvider;
import com.jogodavelha.game.draft.DraftState;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.room.RematchService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoomLeaveServiceTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final DraftStateManager drafts = mock(DraftStateManager.class);
    private final RoomService roomService = mock(RoomService.class);
    private final RematchService rematches = mock(RematchService.class);
    private final GameLifecycleService lifecycle = mock(GameLifecycleService.class);
    private final RoomLeaveService leaves = new RoomLeaveService(rooms, games, drafts, roomService, rematches, lifecycle);
    private final User host = user("host");
    private final User guest = user("guest");
    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("0042", host);
        room.setId(UUID.randomUUID());
        when(rooms.findByCodeAndStatusIn("0042", RoomCodeAllocator.ACTIVE)).thenReturn(Optional.of(room));
        when(games.findByRoomIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(drafts.find(any())).thenReturn(Optional.empty());
    }

    @Test
    void hostLeavingTheWaitingRoomClosesIt() {
        leaves.leave("0042", host.getId());

        verify(roomService).leaveRoom("0042", host.getId());
        verifyNoInteractions(lifecycle, rematches);
    }

    @Test
    void leavingDuringTheMatchFoundPauseClosesTheRoomWithoutAMatch() {
        room.join(guest);

        leaves.leave("0042", guest.getId());

        verify(roomService).closeBeforeMatch(room.getId(), guest.getId());
        verifyNoInteractions(lifecycle, rematches);
    }

    @Test
    void leavingDuringARematchPreparationClosesTheRoomWithoutAMatch() {
        room.join(guest);
        when(games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(room.getId(), GameStatus.FINISHED))
                .thenReturn(Optional.of(mock(Game.class)));
        when(drafts.find(room.getId())).thenReturn(Optional.of(openDraft()));

        leaves.leave("0042", guest.getId());

        verify(roomService).closeBeforeMatch(room.getId(), guest.getId());
        verifyNoInteractions(lifecycle, rematches);
    }

    @Test
    void leavingDuringTheMatchIsAForfeit() {
        room.join(guest);
        Game game = mock(Game.class);
        UUID gameId = UUID.randomUUID();
        when(game.getId()).thenReturn(gameId);
        when(games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS)).thenReturn(Optional.of(game));

        leaves.leave("0042", guest.getId());

        verify(lifecycle).forfeitActiveGame(gameId, guest.getId());
        verifyNoInteractions(rematches);
    }

    @Test
    void leavingTheResultReturnsToTheLobby() {
        room.join(guest);
        when(games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(room.getId(), GameStatus.FINISHED))
                .thenReturn(Optional.of(mock(Game.class)));

        leaves.leave("0042", guest.getId());

        verify(rematches).returnToLobby("0042", guest.getId());
        verifyNoInteractions(lifecycle);
    }

    @Test
    void outsidersAndInvalidCodesAreRejected() {
        assertThrows(AccessDeniedException.class, () -> leaves.leave("0042", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> leaves.leave("42", host.getId()));
        verifyNoInteractions(roomService, lifecycle, rematches);
    }

    private DraftState openDraft() {
        var items = new DraftEligibilityProvider.EligibleItems(
                List.of(UUID.randomUUID(), UUID.randomUUID()), List.of("BOMBA", "ESCUDO"));
        return new DraftState(room.getId(), "0042", 21_000, 0,
                DraftState.Player.initial(host.getId(), items), DraftState.Player.initial(guest.getId(), items));
    }

    private static User user(String name) {
        User user = new User(name, "hash", name + "@example.test");
        user.setId(UUID.randomUUID());
        return user;
    }
}
