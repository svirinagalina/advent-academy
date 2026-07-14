package com.adventacademy.contentservice.domain.constructor;

/**
 * Publication lifecycle shared by the course constructor aggregates
 * ({@link Component}, {@link Course}, {@link Direction}).
 * <p>
 * Every aggregate starts in {@link #DRAFT} and enforces the allowed transitions
 * through {@link #canTransitionTo(PublicationStatus)}. A transition into the same
 * status is never allowed.
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