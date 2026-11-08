package com.jogodavelha.game.draft;

import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
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
    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final DraftCharacterService service = new DraftCharacterService(rooms, drafts, characters);
    private final UUID playerId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();

    @Test
    void rejectsInvalidRoomCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.select("12", playerId, characterId.toString()));
        verifyNoInteractions(rooms, drafts);
    }

    @Test
    void rejectsNullOrUnknownCharacterCode() {
        assertThrows(IllegalArgumentException.class, () -> service.select("0042", playerId, null));
        assertThrows(IllegalArgumentException.class, () -> service.select("0042", playerId, "UNKNOWN"));
        verifyNoInteractions(drafts);
    }

    @Test
    void rejectsPlayerOutsideTheRoom() {
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, playerId)).thenReturn(Optional.empty());
        Character character = character();
        when(characters.findBySymbol(CharacterSymbol.X)).thenReturn(Optional.of(character));
        assertThrows(AccessDeniedException.class,
                () -> service.select("0042", playerId, "X"));
        verifyNoInteractions(drafts);
    }

    @Test
    void delegatesToDraftService() {
        Room room = mock(Room.class);
        UUID roomId = UUID.randomUUID();
        when(room.getId()).thenReturn(roomId);
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, playerId)).thenReturn(Optional.of(room));
        when(characters.findBySymbol(CharacterSymbol.X)).thenReturn(Optional.of(character()));

        service.select("0042", playerId, "X");

        verify(drafts).selectCharacter(roomId, playerId, characterId);
    }

    @Test
    void missingDraftBecomesIllegalState() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(UUID.randomUUID());
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, playerId)).thenReturn(Optional.of(room));
        when(characters.findBySymbol(CharacterSymbol.X)).thenReturn(Optional.of(character()));
        doThrow(new NoSuchElementException()).when(drafts).selectCharacter(any(), any(), any());

        assertThrows(IllegalStateException.class,
                () -> service.select("0042", playerId, "X"));
    }

    private Character character() {
        Character character = new Character("X", CharacterSymbol.X, 0);
        character.setId(characterId);
        return character;
    }
}