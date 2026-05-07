package com.nemonicworld.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordChangeRequest(@NotBlank @Size(min = 8, max = 72) String password) {
}
