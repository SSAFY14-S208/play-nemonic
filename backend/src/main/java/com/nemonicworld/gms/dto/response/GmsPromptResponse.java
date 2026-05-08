package com.nemonicworld.gms.dto.response;

import com.nemonicworld.gms.entity.GmsPrompt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "GMS 프롬프트 응답")
public record GmsPromptResponse(Long id, String name, String content, String featureType, Long createdBy,
    LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static GmsPromptResponse from(GmsPrompt prompt) {
        return new GmsPromptResponse(prompt.getId(), prompt.getName(), prompt.getContent(), prompt.getFeatureType(),
            prompt.getCreatedBy(), prompt.getCreatedAt(), prompt.getUpdatedAt());
    }
}
