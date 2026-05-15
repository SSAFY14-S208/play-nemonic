package com.nemonicworld.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 커뮤니티 캔버스 벽 렌더링에 필요한 메모 목록 항목 응답입니다.
 */
public record CommunityMemoItemResponse(
    @Schema(description = "커뮤니티 메모 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String memoUuid,
    @Schema(description = "작성자 닉네임", example = "망고") String authorNickname,
    @Schema(description = "메모 소스 유형", example = "DIRECT") String sourceType,
    @Schema(description = "대표 메모 이미지 URL", example = "http://localhost/memo-thumb.png") String memoImageUrl,
    @Schema(description = "최종 원본 이미지 URL", example = "http://localhost/memo-original.png") String memoOriginalImageUrl,
    @Schema(description = "최종 썸네일 이미지 URL", example = "http://localhost/memo-thumb.png") String memoThumbnailImageUrl,
    @Schema(description = "갤러리 기반 flipbook 메모 GIF 재생 URL", example = "http://localhost/memo-playback.gif") String memoPlaybackImageUrl,
    @Schema(description = "벽 X 좌표", example = "120.5") double positionX,
    @Schema(description = "벽 Y 좌표", example = "80.0") double positionY,
    @Schema(description = "레이어 순서", example = "3") int zIndex,
    @Schema(description = "회전 각도", example = "-4.5") float rotationDeg,
    @Schema(description = "요청자가 작성한 메모 여부", example = "true") boolean ownedByMe,
    @Schema(description = "부착 시각", example = "2026-05-07T15:30:00") LocalDateTime attachedAt,
    @Schema(description = "프론트가 저장한 메모 표현 보조 JSON", example = "{\"kind\":\"community-direct-v1\",\"memoColor\":\"#ffe887\"}") Map<String, Object> decoration) {
}
