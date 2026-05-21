package com.nemonicworld.gms.entity;

import java.time.LocalDateTime;

public class GmsPrompt {

    private final Long id;
    private final String name;
    private final String content;
    private final String featureType;
    private final Long createdBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime deletedAt;
    private final boolean active;
    private final LocalDateTime activatedAt;
    private final Long activatedBy;

    public GmsPrompt(Long id, String name, String content, String featureType, Long createdBy, LocalDateTime createdAt,
        LocalDateTime updatedAt, LocalDateTime deletedAt, boolean active, LocalDateTime activatedAt, Long activatedBy) {
        this.id = id;
        this.name = name;
        this.content = content;
        this.featureType = featureType;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.active = active;
        this.activatedAt = activatedAt;
        this.activatedBy = activatedBy;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public String getFeatureType() {
        return featureType;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public Long getActivatedBy() {
        return activatedBy;
    }
}
