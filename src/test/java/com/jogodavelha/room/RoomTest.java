package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomTest {
    private final User user = new User("player", "hash", "player@example.test");

    @BeforeEach
    void setUp() {
        user.setId(UUID.randomUUID().toString());
    }

    @Test
    void preservesLeadingZerosAndStartsWaiting() {
        Room room = new Room("0042", user);
        assertEquals("0042", room.getCode());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertNull(room.getPlayer2());
    }

    @Test
    void rejectsInvalidCodeAndPlayer() {
        for (String code : new String[]{"42", "12345", "abcd", "１２３４"}) {
            assertThrows(IllegalArgumentException.class, () -> new Room(code, user));
        }
        assertThrows(IllegalArgumentException.class, () -> new Room(null, user));
        assertThrows(IllegalArgumentException.class, () -> new Room("1234", null));
    }
}
