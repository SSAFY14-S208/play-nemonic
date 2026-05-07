package com.nemonicworld.inquiry.dto.response;

import com.nemonicworld.inquiry.entity.CsInquiry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "관리자 고객 문의 상세 조회 응답")
public record CsInquiryDetailResponse(@Schema(description = "문의 ID", example = "1") Long id,
    @Schema(description = "익명 사용자 UUID", example = "018f6b7a-9b2e-7b2e-9f3a-1c2d3e4f5678") UUID userId,
    @Schema(description = "문의 유형", example = "error") String type,
    @Schema(description = "문의 제목", example = "결제 오류 문의") String title,
    @Schema(description = "문의 내용", example = "결제는 완료됐는데 서비스가 활성화되지 않았습니다.") String content,
    @Schema(description = "답변 받을 이메일", example = "user@example.com") String email,
    @Schema(description = "첨부 파일 URL 목록") List<String> attachments,
    @Schema(description = "문의 작성 환경 메타데이터") Map<String, Object> meta,
    @Schema(description = "문의 상태", example = "new") String status,
    @Schema(description = "담당 관리자 ID", example = "1") Long assignedTo,
    @Schema(description = "관리자 내부 메모 또는 답변 내용") String responseNote,
    @Schema(description = "답변 처리 시각", example = "2026-05-07T12:34:56") LocalDateTime respondedAt,
    @Schema(description = "문의 생성 시각", example = "2026-05-07T12:34:56") LocalDateTime createdAt,
    @Schema(description = "문의 수정 시각", example = "2026-05-07T12:34:56") LocalDateTime updatedAt) {

    public static CsInquiryDetailResponse of(CsInquiry inquiry, List<String> attachments, Map<String, Object> meta) {
        return new CsInquiryDetailResponse(inquiry.getId(), inquiry.getUserId(), inquiry.getType(), inquiry.getTitle(),
            inquiry.getContent(), inquiry.getEmail(), attachments, meta, inquiry.getStatus(), inquiry.getAssignedTo(),
            inquiry.getResponseNote(), inquiry.getRespondedAt(), inquiry.getCreatedAt(), inquiry.getUpdatedAt());
    }
}
