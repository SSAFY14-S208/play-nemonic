package com.nemonicworld.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
    @NotBlank(message = "관리자 아이디를 입력해주세요.") @Size(max = 64, message = "관리자 아이디는 64자 이하로 입력해주세요.") String loginId,

    @NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 255, message = "비밀번호는 255자 이하로 입력해주세요.") String password) {
}
