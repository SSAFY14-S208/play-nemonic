package com.nemonicworld.inquiry.entity;

import java.time.LocalDateTime;
import java.util.UUID;

public class CsInquiry {

    private final Long id;
    private final UUID userId;
    private final String type;
    private final String title;
    private final String content;
    private final String email;
    private final String attachments;
    private final String meta;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CsInquiry(Long id, UUID userId, String type, String title, String content, String email, String attachments,
        String meta, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.email = email;
        this.attachments = attachments;
        this.meta = meta;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getEmail() {
        return email;
    }

    public String getAttachments() {
        return attachments;
    }

    public String getMeta() {
        return meta;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
