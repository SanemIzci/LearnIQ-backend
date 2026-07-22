package com.learniq.exam.exception;

import java.util.UUID;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entityName, UUID id) {
        super(entityName + " not found with id: " + id);
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
