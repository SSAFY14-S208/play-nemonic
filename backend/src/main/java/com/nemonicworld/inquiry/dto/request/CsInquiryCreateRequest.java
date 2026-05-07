package com.nemonicworld.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Schema(description = "CS 문의 접수 요청")
public record CsInquiryCreateRequest(
    @NotBlank(message = "제목 필수") @Size(max = 255, message = "제목 255자 이하") @Schema(description = "문의 제목") String title,

    @NotBlank(message = "문의 유형을 입력해 주세요.") @Schema(description = "문의 유형", example = "error") String type,

    @Size(max = 1000, message = "내용은 1000자 이하입니다.") @Schema(description = "문의 내용") String content,

    @Email(message = "이메일 형식 오류") @Size(max = 255, message = "이메일 255자 이하") @Schema(description = "이메일") String email,

    @Size(max = 3, message = "첨부 파일은 최대 3개입니다.") @Schema(description = "첨부 파일 URL 목록") List<String> attachments,

    @Schema(description = "선택 입력 클라이언트 메타데이터") Map<String, Object> meta) {
}
