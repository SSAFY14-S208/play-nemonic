package com.nemonicworld.inquiry.dto.response;

import com.nemonicworld.inquiry.entity.CsInquiry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 고객 문의 상태 변경 응답")
public record CsInquiryStatusUpdateResponse(@Schema(description = "문의 ID", example = "1") Long id,
    @Schema(description = "문의 상태", example = "in_progress") String status,
    @Schema(description = "수정 시각", example = "2026-05-08T12:34:56") LocalDateTime updatedAt) {

    public static CsInquiryStatusUpdateResponse from(CsInquiry inquiry) {
        return new CsInquiryStatusUpdateResponse(inquiry.getId(), inquiry.getStatus(), inquiry.getUpdatedAt());
    }
}
