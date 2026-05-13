package com.nemonicworld.backoffice.setting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시스템 파라미터 수정 관리자")
public record SystemParameterUpdatedByResponse(Long id, String nickname) {
}
