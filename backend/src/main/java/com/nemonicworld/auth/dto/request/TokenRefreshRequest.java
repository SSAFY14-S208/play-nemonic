package com.nemonicworld.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(@NotBlank(message = "리프레시 토큰을 입력해주세요.") String refreshToken) {
}
