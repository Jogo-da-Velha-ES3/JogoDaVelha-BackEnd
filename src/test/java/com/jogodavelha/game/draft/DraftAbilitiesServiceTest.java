package com.jogodavelha.game.draft;

import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftAbilitiesServiceTest {
    private final UUID roomId = UUID.randomUUID(), playerId = UUID.randomUUID(), opponent = UUID.randomUUID();
    private final UUID x = UUID.randomUUID(), o = UUID.randomUUID();
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final DraftStateManager states = mock(DraftStateManager.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AtomicLong now = new AtomicLong(1000);
    private DraftState state;
    private DraftAbilitiesService service;

    @BeforeEach
    void setUp() {
        var eligible = new DraftEligibilityProvider.EligibleItems(List.of(x, o), List.of("BOMBA", "ESCUDO", "TORNADO"));
        state = new DraftState(roomId, "0042", 21000, 0,
                DraftState.Player.initial(playerId, eligible).withCharacter(x),
                DraftState.Player.initial(opponent, eligible));
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, playerId)).thenReturn(Optional.of(room));
        when(states.update(eq(roomId), any())).thenAnswer(invocation -> {
            UnaryOperator<DraftState> update = invocation.getArgument(1);
            DraftState before = state;
            state = update.apply(before).withVersion(before.version() + 1);
            return Optional.of(new DraftStateManager.Change(before, state));
        });
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        var drafts = new DraftService(states, mock(DraftEligibilityProvider.class), clock,
                mock(TaskScheduler.class), events, new Random(1));
        service = new DraftAbilitiesService(rooms, drafts);
    }

    @Test
    void confirmsTwoEligibleSkillsAndPublishesReadiness() {
        service.confirm("0042", playerId, List.of("BOMBA", "TORNADO"));

        assertEquals(List.of("BOMBA", "TORNADO"), state.player1().initialSkills());
        assertTrue(state.player1().ready());
        assertEquals(List.of(), state.player2().initialSkills());
        assertEquals(21000, state.deadlineEpochMillis());
        verify(events).publishEvent(isA(DraftUpdated.class));
        verify(events, never()).publishEvent(isA(DraftCompleted.class));
    }

    @ParameterizedTest
    @MethodSource("invalidSelections")
    void rejectsMalformedSelectionsWithoutUpdatingDraft(List<String> selection) {
        assertThrows(IllegalArgumentException.class, () -> service.confirm("0042", playerId, selection));
        verifyNoInteractions(rooms, states, events);
    }

    static Stream<List<String>> invalidSelections() {
        return Stream.of(null, List.of(), List.of("BOMBA"), List.of("BOMBA", "ESCUDO", "TORNADO"),
                List.of("BOMBA", "BOMBA"), List.of("BOMBA", " "), Arrays.asList("BOMBA", null));
    }

    @Test
    void rejectsSkillsOutsideTheEligibleInventoryWithoutChangingState() {
        DraftState before = state;
        assertThrows(IllegalArgumentException.class,
                () -> service.confirm("0042", playerId, List.of("BOMBA", "SILENCIO")));
        assertEquals(before, state);
        verifyNoInteractions(events);
    }

    @Test
    void requiresCharacterBeforeConfirmingSkills() {
        var initial = DraftState.Player.initial(playerId,
                new DraftEligibilityProvider.EligibleItems(List.of(x, o), List.of("BOMBA", "ESCUDO")));
        state = state.withPlayer(initial);
        assertThrows(IllegalArgumentException.class,
                () -> service.confirm("0042", playerId, List.of("BOMBA", "ESCUDO")));
        assertFalse(state.player1().ready());
        verifyNoInteractions(events);
    }

    @Test
    void rejectsChangesAfterConfirmation() {
        service.confirm("0042", playerId, List.of("BOMBA", "ESCUDO"));
        DraftState confirmed = state;
        clearInvocations(events);
        assertThrows(IllegalStateException.class,
                () -> service.confirm("0042", playerId, List.of("BOMBA", "TORNADO")));
        assertEquals(confirmed, state);
        verifyNoInteractions(events);
    }

    @Test
    void rejectsConfirmationAtDeadline() {
        now.set(21000);
        assertThrows(IllegalStateException.class,
                () -> service.confirm("0042", playerId, List.of("BOMBA", "ESCUDO")));
        assertFalse(state.player1().ready());
        verifyNoInteractions(events);
    }

    @Test
    void rejectsNonMemberBeforeAccessingDraft() {
        assertThrows(AccessDeniedException.class,
                () -> service.confirm("0042", opponent, List.of("BOMBA", "ESCUDO")));
        verifyNoInteractions(states, events);
    }

    @Test
    void rejectsInvalidRoomCodeBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.confirm("42", playerId, List.of("BOMBA", "ESCUDO")));
        verifyNoInteractions(rooms, states, events);
    }

    @Test
    void unavailableDraftReturnsConflictInsteadOfUnexpectedError() {
        when(states.update(eq(roomId), any())).thenThrow(new NoSuchElementException("Draft não encontrado"));
        assertThrows(IllegalStateException.class,
                () -> service.confirm("0042", playerId, List.of("BOMBA", "ESCUDO")));
        verifyNoInteractions(events);
    }
}
