package com.adventacademy.contentservice.domain.constructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Ordered reference from a {@link Course} to a {@link Component}.
 * <p>
 * An immutable value object carrying the referenced {@code componentId} and its
 * {@code position} within the owning course, so ordering is part of the domain model.
 * Two references are equal when they point to the same component at the same position.
 */
public final class ComponentRef {

    private final UUID componentId;
    private final int position;

    private ComponentRef(UUID componentId, int position) {
        this.componentId = componentId;
        this.position = position;
    }

    public static ComponentRef create(UUID componentId, int position) {
        if (componentId == null) {
            throw new IllegalArgumentException("componentId must not be null");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        return new ComponentRef(componentId, position);
    }

    public static ComponentRef restore(UUID componentId, int position) {
        return new ComponentRef(componentId, position);
    }

    public UUID getComponentId() {
        return componentId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComponentRef other)) {
            return false;
        }
        return position == other.position && Objects.equals(componentId, other.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId, position);
    }
}
