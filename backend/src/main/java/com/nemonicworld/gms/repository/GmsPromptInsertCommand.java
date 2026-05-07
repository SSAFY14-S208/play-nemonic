package com.nemonicworld.gms.repository;

import java.time.LocalDateTime;

public record GmsPromptInsertCommand(String name, String content, String featureType, Long createdBy,
    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
