package com.jogodavelha.room;

public class RoomAlreadyStartedException extends IllegalStateException {

    public RoomAlreadyStartedException(String message) {
        super(message);
    }
}