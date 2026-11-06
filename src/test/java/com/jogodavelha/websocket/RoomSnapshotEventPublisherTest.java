package com.jogodavelha.websocket;

import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.draft.DraftState;
import com.jogodavelha.game.draft.DraftUpdated;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class RoomSnapshotEventPublisherTest {
    private final RoomSnapshotService snapshots = mock(RoomSnapshotService.class);
    private final RoomSnapshotEventPublisher publisher = new RoomSnapshotEventPublisher(snapshots);

    @Test
    void publishesSnapshotForDraftStateChanges() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        var event = new DraftUpdated(UUID.randomUUID(), "0042", 21_000L, 2L, List.of(
                new DraftUpdated.PlayerStatus(p1, UUID.randomUUID(), DraftState.Stage.SKILLS, false),
                new DraftUpdated.PlayerStatus(p2, null, DraftState.Stage.CHARACTER, false)));

        publisher.onDraftUpdated(event);

        verify(snapshots).publish("0042");
    }

    @Test
    void doesNotPublishSnapshotsOnGameplayTurnChanges() {
        assertFalse(java.util.Arrays.stream(RoomSnapshotEventPublisher.class.getDeclaredMethods())
                .anyMatch(method -> java.util.Arrays.asList(method.getParameterTypes()).contains(TurnChangedEvent.class)));
        verifyNoInteractions(snapshots);
    }
}
