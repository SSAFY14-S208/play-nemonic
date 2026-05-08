package com.nemonicworld.backoffice.setting.entity;

import java.time.LocalDateTime;

public record SystemParameter(Long id, String key, String value, Long updatedById, String updatedByNickname,
    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
