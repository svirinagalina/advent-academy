package com.adventacademy.contentservice.domain;

public enum ContentStatus {

    DRAFT,
    REVIEW,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(ContentStatus target) {
        if (target == null) {
            return false;
        }

        return switch (this) {
            case DRAFT -> (target == REVIEW || target == ARCHIVED);
            case REVIEW -> (target == DRAFT || target == PUBLISHED || target == ARCHIVED);
            case PUBLISHED -> (target == ARCHIVED);
            case ARCHIVED -> false;
        };
    }
}