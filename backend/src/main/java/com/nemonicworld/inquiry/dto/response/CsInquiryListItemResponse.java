package com.nemonicworld.inquiry.dto.response;

import com.nemonicworld.inquiry.entity.CsInquiry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "관리자 고객 문의 목록 항목 응답")
public record CsInquiryListItemResponse(@Schema(description = "문의 ID", example = "1") Long id,
    @Schema(description = "익명 사용자 UUID", example = "018f6b7a-9b2e-7b2e-9f3a-1c2d3e4f5678") UUID userId,
    @Schema(description = "문의 유형", example = "error") String type,
    @Schema(description = "문의 제목", example = "결제 오류 문의") String title,
    @Schema(description = "답변 받을 이메일", example = "user@example.com") String email,
    @Schema(description = "문의 상태", example = "new") String status,
    @Schema(description = "문의 생성 시각", example = "2026-05-07T12:34:56") LocalDateTime createdAt,
    @Schema(description = "문의 수정 시각", example = "2026-05-07T12:34:56") LocalDateTime updatedAt) {

    public static CsInquiryListItemResponse from(CsInquiry inquiry) {
        return new CsInquiryListItemResponse(inquiry.getId(), inquiry.getUserId(), inquiry.getType(),
            inquiry.getTitle(), inquiry.getEmail(), inquiry.getStatus(), inquiry.getCreatedAt(),
            inquiry.getUpdatedAt());
    }
}
