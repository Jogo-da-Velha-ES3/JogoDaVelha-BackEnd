package com.jogodavelha.room;

public class RoomFullException extends IllegalStateException {

    public RoomFullException(String message) {
        super(message);
    }
}