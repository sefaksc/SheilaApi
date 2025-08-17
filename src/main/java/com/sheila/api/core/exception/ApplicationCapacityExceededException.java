package com.sheila.api.core.exception;

public class ApplicationCapacityExceededException extends RuntimeException {
    public ApplicationCapacityExceededException(String appIdOrName) {
        super("Application capacity exceeded for: " + appIdOrName);
    }
}
