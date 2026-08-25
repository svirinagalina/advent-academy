package com.adventacademy.userservice.domain.exception;

public class SelfApprovalNotAllowedException extends RuntimeException {
    public SelfApprovalNotAllowedException(String message) {
        super(message);
    }
}
