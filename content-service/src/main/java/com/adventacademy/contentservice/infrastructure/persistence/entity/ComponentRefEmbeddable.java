package com.adventacademy.contentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class ComponentRefEmbeddable {

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(nullable = false)
    private int position;

    public ComponentRefEmbeddable() {
    }

    public ComponentRefEmbeddable(UUID componentId, int position) {
        this.componentId = componentId;
        this.position = position;
    }

    public UUID getComponentId() {
        return componentId;
    }

    public int getPosition() {
        return position;
    }

    public void setComponentId(UUID componentId) {
        this.componentId = componentId;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}