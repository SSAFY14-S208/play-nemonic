package com.nemonicworld.inquiry.dto.response;

import com.nemonicworld.inquiry.entity.CsInquiry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 고객 문의 이메일 회신 응답")
public record CsInquiryReplyResponse(@Schema(description = "문의 ID", example = "1") Long id,
    @Schema(description = "문의 상태", example = "resolved") String status,
    @Schema(description = "답변 처리 시각", example = "2026-05-07T12:34:56") LocalDateTime respondedAt) {

    public static CsInquiryReplyResponse from(CsInquiry inquiry) {
        return new CsInquiryReplyResponse(inquiry.getId(), inquiry.getStatus(), inquiry.getRespondedAt());
    }
}
