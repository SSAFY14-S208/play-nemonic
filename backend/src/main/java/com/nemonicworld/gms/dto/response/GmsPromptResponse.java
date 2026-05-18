package com.nemonicworld.gms.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemonicworld.gms.entity.GmsPrompt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "GMS 프롬프트 응답")
public class GmsPromptResponse {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_NOT_ACTIVE = "not_active";

    @Schema(description = "GMS 프롬프트 ID. 기본 대체 프롬프트는 null입니다.", example = "5", nullable = true)
    private final Long id;

    @Schema(description = "프롬프트 이름", example = "오늘의 운세 기본 프롬프트 v2")
    private final String name;

    @Schema(description = "GMS 호출에 사용할 프롬프트 본문", example = "만세력 결과를 바탕으로 오늘의 운세를 생성한다.")
    private final String content;

    @Schema(description = "프롬프트가 적용될 기능 타입", example = "fortune")
    private final String featureType;

    @Schema(description = "생성한 관리자 ID", example = "1", nullable = true)
    private final Long createdBy;

    @Schema(description = "생성 시각", example = "2026-05-18T14:00:00", nullable = true)
    private final LocalDateTime createdAt;

    @Schema(description = "수정 시각", example = "2026-05-18T14:10:00", nullable = true)
    private final LocalDateTime updatedAt;

    @Schema(description = "현재 서비스에서 사용 중인 프롬프트 여부", example = "true")
    @JsonProperty("isActive")
    private final boolean active;

    @Schema(description = "프론트 표시용 활성 상태", example = "active", allowableValues = {"active", "not_active"})
    private final String status;

    @Schema(description = "활성화 시각", example = "2026-05-18T14:20:00", nullable = true)
    private final LocalDateTime activatedAt;

    @Schema(description = "활성화한 관리자 ID", example = "1", nullable = true)
    private final Long activatedBy;

    public GmsPromptResponse(Long id, String name, String content, String featureType, Long createdBy,
        LocalDateTime createdAt, LocalDateTime updatedAt, boolean active, String status, LocalDateTime activatedAt,
        Long activatedBy) {
        this.id = id;
        this.name = name;
        this.content = content;
        this.featureType = featureType;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.active = active;
        this.status = status;
        this.activatedAt = activatedAt;
        this.activatedBy = activatedBy;
    }

    public static GmsPromptResponse from(GmsPrompt prompt) {
        return new GmsPromptResponse(prompt.getId(), prompt.getName(), prompt.getContent(), prompt.getFeatureType(),
            prompt.getCreatedBy(), prompt.getCreatedAt(), prompt.getUpdatedAt(), prompt.isActive(),
            prompt.isActive() ? STATUS_ACTIVE : STATUS_NOT_ACTIVE, prompt.getActivatedAt(), prompt.getActivatedBy());
    }

    public static GmsPromptResponse defaultFortune(String content) {
        return new GmsPromptResponse(null, "기본 오늘의 운세 프롬프트", content, "fortune", null, null, null, true, STATUS_ACTIVE,
            null, null);
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

    @JsonProperty("isActive")
    public boolean isActive() {
        return active;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public Long getActivatedBy() {
        return activatedBy;
    }
}
