package com.nemonicworld.user.dto.response;

import java.time.LocalDateTime;

public record AnonymousUserResponse(String userUuid, String nickname, LocalDateTime createdAt) {
}
