package com.jogodavelha.game.draft;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DraftRecovery {
    private final DraftStateManager states;
    private final DraftService drafts;

    public DraftRecovery(DraftStateManager states, DraftService drafts) {
        this.states = states;
        this.drafts = drafts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeActiveDrafts() {
        states.findAllRoomIds().forEach(drafts::resume);
    }
}
