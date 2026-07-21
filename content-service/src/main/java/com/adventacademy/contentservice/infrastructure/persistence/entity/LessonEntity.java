package com.adventacademy.contentservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lessons")
public class LessonEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    private ComponentEntity component;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    public LessonEntity() {
    }

    public UUID getId() {
        return id;
    }

    public ComponentEntity getComponent() {
        return component;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setComponent(ComponentEntity component) {
        this.component = component;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
