package com.adventacademy.contentservice.application.service;

import java.util.UUID;

public class ComponentInUseException extends RuntimeException {

    public ComponentInUseException(UUID componentId) {
        super("Component is used by at least one course and cannot be deleted: " + componentId);
    }
}