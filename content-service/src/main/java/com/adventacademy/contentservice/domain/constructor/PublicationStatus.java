package com.adventacademy.contentservice.domain.constructor;

/**
 * Publication lifecycle of the publishable constructor aggregates
 * ({@link Component} and {@link Course}).
 * <p>
 * {@link Direction} intentionally has no publication status: it is visible in the
 * catalog as long as it contains at least one published course.
 * <p>
 * Every publishable aggregate starts in {@link #DRAFT} and enforces the allowed
 * transitions through {@link #canTransitionTo(PublicationStatus)}. A transition into
 * the same status is never allowed.
 */
public enum PublicationStatus {

    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(PublicationStatus target) {
        if (target == null) {
            return false;
        }

        return switch (this) {
            case DRAFT -> (target == PUBLISHED || target == ARCHIVED);
            case PUBLISHED -> (target == ARCHIVED);
            case ARCHIVED -> false;
        };
    }
}