package com.nemonicworld.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 고객 문의 상태 변경 요청")
public record CsInquiryStatusUpdateRequest(@NotBlank(message = "문의 상태를 입력해 주세요.") String status) {
}
