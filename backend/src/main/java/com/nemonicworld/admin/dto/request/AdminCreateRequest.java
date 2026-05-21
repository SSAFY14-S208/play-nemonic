package com.nemonicworld.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateRequest(@NotBlank @Size(max = 64) String loginId,

    @NotBlank @Size(min = 8, max = 72) String password,

    @NotBlank @Size(max = 20) String nickname,

    @NotBlank @Email @Size(max = 255) String email,

    @Size(max = 32) String role) {

    @Override
    @Schema(description = "생성할 관리자 권한입니다. 생략하면 admin으로 생성합니다.", allowableValues = {"admin",
        "viewer"}, example = "viewer")
    public String role() {
        return role;
    }
}
