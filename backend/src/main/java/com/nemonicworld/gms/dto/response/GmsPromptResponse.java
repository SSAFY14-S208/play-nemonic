package com.nemonicworld.gms.dto.response;

import com.nemonicworld.gms.entity.GmsPrompt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "GMS prompt response")
public record GmsPromptResponse(Long id, String name, String content, String featureType, Long createdBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, boolean isActive, String status, LocalDateTime activatedAt,
    Long activatedBy) {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_NOT_ACTIVE = "not_active";

    public static GmsPromptResponse from(GmsPrompt prompt) {
        return new GmsPromptResponse(prompt.getId(), prompt.getName(), prompt.getContent(), prompt.getFeatureType(),
            prompt.getCreatedBy(), prompt.getCreatedAt(), prompt.getUpdatedAt(), prompt.isActive(),
            prompt.isActive() ? STATUS_ACTIVE : STATUS_NOT_ACTIVE, prompt.getActivatedAt(), prompt.getActivatedBy());
    }

    public static GmsPromptResponse defaultFortune(String content) {
        return new GmsPromptResponse(null, "Default fortune prompt", content, "fortune", null, null, null, true,
            STATUS_ACTIVE, null, null);
    }
}
