package com.nemonicworld.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.constraints.URL;

@Schema(description = "CS 문의 접수 요청")
public record CsInquiryCreateRequest(
    @NotBlank @Size(max = 255) @Schema(description = "문의 제목", example = "결제 오류 문의") String title,

    @NotBlank @Schema(description = "문의 유형", example = "error") String type,

    @Size(max = 1000) @Schema(description = "문의 내용", example = "결제했는데 서비스가 활성화되지 않았습니다.") String content,

    @Email @Size(max = 255) @Schema(description = "선택 입력 답변 이메일", example = "user@example.com") String email,

    @Size(max = 3) @Schema(description = "첨부 파일 URL 목록") List<@NotBlank @URL String> attachments,

    @Schema(description = "선택 입력 클라이언트 메타데이터") Map<String, Object> meta) {
}
