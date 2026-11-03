package com.jogodavelha.game.draft;

import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DraftCharacterServiceTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final DraftService drafts = mock(DraftService.class);
    private final DraftCharacterService service = new DraftCharacterService(rooms, drafts);
    private final UUID playerId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();

    @Test
    void rejectsInvalidRoomCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.select("12", playerId, characterId.toString()));
        verifyNoInteractions(rooms, drafts);
    }

    @Test
    void rejectsNullOrMalformedCharacterId() {
        assertThrows(IllegalArgumentException.class, () -> service.select("0042", playerId, null));
        assertThrows(IllegalArgumentException.class, () -> service.select("0042", playerId, "nao-e-uuid"));
        verifyNoInteractions(drafts);
    }

    @Test
    void rejectsPlayerOutsideTheRoom() {
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, playerId)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,
                () -> service.select("0042", playerId, characterId.toString()));
        verifyNoInteractions(drafts);
    }

    @Test
    void delegatesToDraftService() {
        Room room = mock(Room.class);
        UUID roomId = UUID.randomUUID();
        when(room.getId()).thenReturn(roomId);
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, playerId)).thenReturn(Optional.of(room));

        service.select("0042", playerId, characterId.toString());

        verify(drafts).selectCharacter(roomId, playerId, characterId);
    }

    @Test
    void missingDraftBecomesIllegalState() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(UUID.randomUUID());
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, playerId)).thenReturn(Optional.of(room));
        doThrow(new NoSuchElementException()).when(drafts).selectCharacter(any(), any(), any());

        assertThrows(IllegalStateException.class,
                () -> service.select("0042", playerId, characterId.toString()));
    }
}