package com.adventacademy.contentservice.infrastructure.persistence.entity;

import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class CourseEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "course_components", joinColumns = @JoinColumn(name = "course_id"))
    @OrderBy("position ASC")
    private List<ComponentRefEmbeddable> components = new ArrayList<>();

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CourseEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public List<ComponentRefEmbeddable> getComponents() {
        return components;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStatus(PublicationStatus status) {
        this.status = status;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public void setComponents(List<ComponentRefEmbeddable> components) {
        this.components = components;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}