package com.adventacademy.userservice.domain.exception;

import java.time.LocalDateTime;

public class AccountLockedException extends RuntimeException {
    private final LocalDateTime lockedUntil;

    public AccountLockedException(LocalDateTime lockedUntil) {
        super("Account is locked until: " + lockedUntil);
        this.lockedUntil = lockedUntil;
    }

    public AccountLockedException(String message) {
        super(message);
        this.lockedUntil = null;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
