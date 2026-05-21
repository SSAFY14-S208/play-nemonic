package com.nemonicworld.gms.repository;

import java.time.LocalDateTime;

public record GmsPromptUpdateCommand(Long id, String name, String content, String featureType,
    LocalDateTime updatedAt) {
}
