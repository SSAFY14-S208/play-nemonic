package com.nemonicworld.inquiry.dto.response;

import com.nemonicworld.inquiry.entity.CsInquiry;
import java.time.LocalDateTime;

public record CsInquiryCreateResponse(Long id, String status, LocalDateTime createdAt) {

    public static CsInquiryCreateResponse from(CsInquiry inquiry) {
        return new CsInquiryCreateResponse(inquiry.getId(), inquiry.getStatus(), inquiry.getCreatedAt());
    }
}
