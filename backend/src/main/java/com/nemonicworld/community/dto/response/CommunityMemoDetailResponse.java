package com.nemonicworld.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 커뮤니티 캔버스 메모 상세 응답입니다.
 */
public record CommunityMemoDetailResponse(
    @Schema(description = "커뮤니티 메모 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String memoUuid,
    @Schema(description = "작성자 닉네임", example = "망고") String authorNickname,
    @Schema(description = "메모 소스 유형", example = "GALLERY") String sourceType,
    @Schema(description = "메모 이미지 URL", example = "http://localhost/memo.png") String memoImageUrl,
    @Schema(description = "벽 X 좌표", example = "120.5") double positionX,
    @Schema(description = "벽 Y 좌표", example = "80.0") double positionY,
    @Schema(description = "레이어 순서", example = "3") int zIndex,
    @Schema(description = "회전 각도", example = "-4.5") float rotationDeg,
    @Schema(description = "요청자가 작성한 메모 여부", example = "true") boolean ownedByMe,
    @Schema(description = "부착 시각", example = "2026-05-07T15:30:00") LocalDateTime attachedAt,
    @Schema(description = "프론트 데코레이션 JSON 객체") Map<String, Object> decoration,
    @Schema(description = "갤러리 원본 artifact UUID", example = "1bce93a2-9c38-4604-80f9-b89be3ec2e53") String artifactId,
    @Schema(description = "갤러리 원본 결과물 종류", example = "relay_drawing") String galleryContentKind,
    @Schema(description = "모더레이션 상태", example = "pending") String moderationStatus,
    @Schema(description = "신고 횟수", example = "0") int reportCount,
    @Schema(description = "생성 시각", example = "2026-05-07T15:30:00") LocalDateTime createdAt,
    @Schema(description = "수정 시각", example = "2026-05-07T15:30:00") LocalDateTime updatedAt) {
}
