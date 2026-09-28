package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

class RoomCodeAllocatorTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final RoomCodeAllocator allocator = new RoomCodeAllocator(rooms, games, mock(JdbcTemplate.class));
    private final User user = new User("player", "hash", "player@example.test");

    @BeforeEach
    void setUp() {
        user.setId(UUID.randomUUID());
    }

    @Test
    void skipsOccupiedCodeBeforePersisting() {
        when(rooms.existsByCodeAndStatusIn(anyString(), anyCollection())).thenReturn(true, false);
        when(rooms.saveAndFlush(any(Room.class))).thenAnswer(call -> call.getArgument(0));
        Room room = allocator.reserve(user);
        var codes = ArgumentCaptor.forClass(String.class);
        verify(rooms, times(2)).existsByCodeAndStatusIn(codes.capture(), anyCollection());
        assertNotEquals(codes.getAllValues().get(0), room.getCode());
        assertEquals(codes.getAllValues().get(1), room.getCode());
        assertTrue(room.getCode().matches("[0-9]{4}"));
    }

    @Test
    void exhaustedSpaceDoesNotLoopForeverOrPersist() {
        when(rooms.existsByCodeAndStatusIn(anyString(), anyCollection())).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> allocator.reserve(user));
        verify(rooms, times(10000)).existsByCodeAndStatusIn(anyString(), anyCollection());
        verify(rooms, never()).saveAndFlush(any());
    }
}
