package com.nemonicworld.invite.service.support;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.GoneException;
import com.nemonicworld.invite.redis.InviteMetadata;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InviteCodeSupport {

    private static final Pattern INVITE_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    private static final String INVALID_INVITE_CODE_MESSAGE = "유효하지 않은 초대코드 형식입니다.";
    private static final String INVITE_EXPIRED_MESSAGE = "만료된 초대코드입니다.";

    public void validateInviteCode(String inviteCode) {
        if (!StringUtils.hasText(inviteCode) || !INVITE_CODE_PATTERN.matcher(inviteCode).matches()) {
            throw new BadRequestException(INVALID_INVITE_CODE_MESSAGE);
        }
    }

    public void validateNotExpired(InviteMetadata invite) {
        if (invite.expiresAt() != null && !invite.expiresAt().isAfter(LocalDateTime.now())) {
            throw new GoneException(INVITE_EXPIRED_MESSAGE);
        }
    }

    public String normalizeBoothType(String boothType) {
        return StringUtils.hasText(boothType) ? boothType.trim().toLowerCase(Locale.ROOT) : "";
    }
}
