package com.jogodavelha.game.draft;

import com.jogodavelha.room.PlayerJoinedRoomEvent;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;

@Component
public class RoomDraftInitializer {
    /** "MATCH FEITO!": os dois jogadores veem o adversário antes de os 20s da Preparação começarem. */
    static final long MATCH_FOUND_MILLIS = 3_000;

    private final DraftService drafts;
    private final RoomRepository rooms;
    private final TaskScheduler scheduler;
    private final Clock clock;

    public RoomDraftInitializer(DraftService drafts, RoomRepository rooms,
                                @Qualifier("turnTaskScheduler") TaskScheduler scheduler, Clock clock) {
        this.drafts = drafts;
        this.rooms = rooms;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerJoined(PlayerJoinedRoomEvent event) {
        scheduler.schedule(() -> startIfStillOpen(event), Instant.ofEpochMilli(clock.millis() + MATCH_FOUND_MILLIS));
    }

    /** Alguém pode ter saído durante o MATCH FEITO: sala encerrada não abre a Preparação. */
    private void startIfStillOpen(PlayerJoinedRoomEvent event) {
        boolean open = rooms.findById(event.roomId()).filter(room -> room.getStatus() == RoomStatus.IN_GAME).isPresent();
        if (open) drafts.start(event.roomId(), event.roomCode(), event.host().id(), event.guest().id());
    }
}
