package com.adventacademy.userservice.domain.exception;

import java.util.Collection;

public class InvalidSortFieldException extends RuntimeException {

    public InvalidSortFieldException(String field, Collection<String> allowed) {
        super("Unknown sort field '" + field + "'. Allowed: " + allowed);
    }
}
