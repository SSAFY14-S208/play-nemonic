package com.nemonicworld.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAccountCreateRequest(@NotBlank @Size(max = 64) String loginId,

    @NotBlank @Size(min = 8, max = 72) String password,

    @NotBlank @Size(max = 20) String nickname,

    @NotBlank @Email @Size(max = 255) String email) {
}
