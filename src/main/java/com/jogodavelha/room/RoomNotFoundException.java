package com.jogodavelha.room;

public class RoomNotFoundException extends IllegalArgumentException {

    public RoomNotFoundException(String message) {
        super(message);
    }
}
