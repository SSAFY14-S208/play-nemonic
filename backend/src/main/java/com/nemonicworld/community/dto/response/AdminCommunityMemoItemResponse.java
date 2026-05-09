package com.nemonicworld.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 관리자 커뮤니티 메모 목록의 운영용 항목 응답입니다.
 */
public record AdminCommunityMemoItemResponse(
    @Schema(description = "커뮤니티 메모 UUID", example = "3f22df4f-b187-42dd-81f3-d9f2fa5ce001") String memoId,
    @Schema(description = "작성자 UUID", example = "550e8400-e29b-41d4-a716-446655440000") String authorUserUuid,
    @Schema(description = "작성자 닉네임", example = "망고") String authorNickname,
    @Schema(description = "메모 출처 유형", example = "DIRECT") String sourceType,
    @Schema(description = "출처 artifact UUID", example = "1bce93a2-9c38-4604-80f9-b89be3ec2e53") String artifactId,
    @Schema(description = "출처 artifact 종류", example = "relay_drawing") String artifactKind,
    @Schema(description = "대표 메모 이미지 URL", example = "http://localhost/memo-thumb.png") String memoImageUrl,
    @Schema(description = "최종 원본 이미지 URL", example = "http://localhost/memo-original.png") String memoOriginalImageUrl,
    @Schema(description = "최종 썸네일 이미지 URL", example = "http://localhost/memo-thumb.png") String memoThumbnailImageUrl,
    @Schema(description = "캔버스 X 좌표", example = "120.5") double positionX,
    @Schema(description = "캔버스 Y 좌표", example = "-30.0") double positionY,
    @Schema(description = "레이어 순서", example = "12") int zIndex,
    @Schema(description = "회전 각도", example = "5.5") float rotationDeg,
    @Schema(description = "신고 누적 수", example = "5") int reportCount,
    @JsonProperty("isHidden") @Schema(description = "숨김 여부", example = "true") boolean isHidden,
    @Schema(description = "숨김 사유", example = "report_threshold") String hiddenReason,
    @Schema(description = "숨김 처리 시각", example = "2026-05-09T14:10:00") LocalDateTime hiddenAt,
    @Schema(description = "모더레이션 상태", example = "allowed") String moderationStatus,
    @Schema(description = "OCR 텍스트", example = "인식된 텍스트") String ocrText,
    @Schema(description = "OCR 분류 결과 JSON 문자열", example = "[\"safe\"]") String ocrCategories,
    @Schema(description = "마지막 검토 관리자 ID", example = "1") Long reviewedBy,
    @Schema(description = "마지막 검토 시각", example = "2026-05-09T14:10:00") LocalDateTime reviewedAt,
    @Schema(description = "부착 시각", example = "2026-05-07T10:20:00") LocalDateTime attachedAt,
    @Schema(description = "생성 시각", example = "2026-05-07T10:20:00") LocalDateTime createdAt,
    @Schema(description = "수정 시각", example = "2026-05-09T14:10:00") LocalDateTime updatedAt) {
}
