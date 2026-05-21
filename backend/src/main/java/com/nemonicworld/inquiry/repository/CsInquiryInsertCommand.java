package com.nemonicworld.inquiry.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public record CsInquiryInsertCommand(UUID userId, String type, String title, String content, String email,
    String attachments, String meta, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
