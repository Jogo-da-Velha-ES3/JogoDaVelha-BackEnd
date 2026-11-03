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
        user.setId(UUID.randomUUID());
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

    @Test
    void joinRejectsAlreadyStartedRoomBeforeCheckingPlayer2() {
        Room room = new Room("0042", user);
        room.setStatus(RoomStatus.IN_GAME);

        User otherUser = new User("other", "hash", "other@example.test");
        otherUser.setId(UUID.randomUUID());

        var error = assertThrows(
                RoomAlreadyStartedException.class,
                () -> room.join(otherUser)
        );

        assertEquals("A partida desta sala já foi iniciada.", error.getMessage());
    }

    @Test
    void closeChangesStatusToClosed() {
        Room room = new Room("0042", user);
        room.close();
        assertEquals(RoomStatus.CLOSED, room.getStatus());
    }

    @Test
    void closeIsIdempotent() {
        Room room = new Room("0042", user);
        room.close();
        assertDoesNotThrow(room::close);
        assertEquals(RoomStatus.CLOSED, room.getStatus());
    }

    @Test
    void joinFailsOnClosedRoom() {
        Room room = new Room("0042", user);
        room.close();

        User guest = new User("guest", "hash", "guest@example.test");
        guest.setId(UUID.randomUUID());

        assertThrows(RuntimeException.class, () -> room.join(guest));
        assertNull(room.getPlayer2());
    }
}
