package com.jogodavelha.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testAddCoinsPositiveAmount() {
        User user = new User("test", "pass", "test@test.com");
        user.setCoinsBalance(100);

        user.addCoins(50);

        assertEquals(150, user.getCoinsBalance());
    }

    @Test
    void testAddCoinsZeroAmount() {
        User user = new User("test", "pass", "test@test.com");
        user.setCoinsBalance(100);

        user.addCoins(0);

        assertEquals(100, user.getCoinsBalance());
    }

    @Test
    void testAddCoinsNegativeAmountThrowsException() {
        User user = new User("test", "pass", "test@test.com");
        user.setCoinsBalance(100);

        assertThrows(IllegalArgumentException.class, () -> {
            user.addCoins(-10);
        });

        assertEquals(100, user.getCoinsBalance());
    }

    @Test
    void testAddCoinsToZeroBalance() {
        User user = new User("test", "pass", "test@test.com");
        user.setCoinsBalance(0);

        user.addCoins(100);

        assertEquals(100, user.getCoinsBalance());
    }
}
