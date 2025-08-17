package com.sheila.api.core.exception;

public class RoomFullException extends RuntimeException {
    public RoomFullException(String roomName) {
        super("Room is full: " + roomName);
    }
}
