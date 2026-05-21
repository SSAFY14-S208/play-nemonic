package com.nemonicworld.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 고객 문의 이메일 회신 요청")
public record CsInquiryReplyRequest(
    @NotBlank(message = "이메일 제목을 입력해 주세요.") @Size(max = 255, message = "이메일 제목은 255자 이하입니다.") String subject,
    @NotBlank(message = "회신 내용을 입력해 주세요.") @Size(max = 5000, message = "회신 내용은 5000자 이하입니다.") String message) {
}
