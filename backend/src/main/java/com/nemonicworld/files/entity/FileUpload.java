package com.nemonicworld.files.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_upload")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileUpload {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private FileUploadPurpose purpose;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FileUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static FileUpload createPending(UUID id, UUID userId, FileUploadPurpose purpose, String originalFileName,
        String contentType, long byteSize, String objectKey, LocalDateTime expiresAt, LocalDateTime now) {
        FileUpload fileUpload = new FileUpload();
        fileUpload.id = id;
        fileUpload.userId = userId;
        fileUpload.purpose = purpose;
        fileUpload.originalFileName = originalFileName;
        fileUpload.contentType = contentType;
        fileUpload.byteSize = byteSize;
        fileUpload.objectKey = objectKey;
        fileUpload.status = FileUploadStatus.PENDING;
        fileUpload.expiresAt = expiresAt;
        fileUpload.createdAt = now;
        fileUpload.updatedAt = now;
        return fileUpload;
    }

    public void markUploaded(LocalDateTime now) {
        this.status = FileUploadStatus.UPLOADED;
        this.updatedAt = now;
    }

    public void markDeleted(LocalDateTime now) {
        this.status = FileUploadStatus.DELETED;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    public boolean isPending() {
        return this.status == FileUploadStatus.PENDING;
    }

    public boolean isUploaded() {
        return this.status == FileUploadStatus.UPLOADED;
    }

    public boolean isDeleted() {
        return this.deletedAt != null || this.status == FileUploadStatus.DELETED;
    }

    public boolean hasPurpose(FileUploadPurpose purpose) {
        return this.purpose == purpose;
    }

}
