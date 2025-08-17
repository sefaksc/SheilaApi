package com.sheila.api.core.exception;

public class AppNotFoundException extends RuntimeException {
    public AppNotFoundException(String key) {
        super("Application not found for key: " + key);
    }
}
