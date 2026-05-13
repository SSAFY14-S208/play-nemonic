package com.nemonicworld.invite.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.GoneException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 초대코드 검증과 협동 부스 입장 처리를 담당하는 서비스 구현체입니다.
 *
 * <p>
 * 초대코드와 게임방 상태는 Redis를 기준으로 조회하고, 사용자 UUID 존재 여부만 DB에서 확인합니다. 부스별 실제 입장 처리는
 * InviteJoinHandler 구현체에 위임합니다.
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private static final Pattern INVITE_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    private static final String INVALID_INVITE_CODE_MESSAGE = "유효하지 않은 초대코드 형식입니다.";
    private static final String INVITE_NOT_FOUND_MESSAGE = "초대코드를 찾을 수 없습니다.";
    private static final String INVITE_EXPIRED_MESSAGE = "만료된 초대코드입니다.";
    private static final String UNSUPPORTED_BOOTH_TYPE_MESSAGE = "지원하지 않는 부스 타입입니다.";

    private final InviteRepository inviteRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final List<InviteJoinHandler> inviteJoinHandlers;

    /**
     * 초대코드와 요청 사용자를 검증한 뒤 연결된 협동 부스에 입장시킵니다.
     */
    @Transactional(readOnly = true)
    @Override
    public InviteJoinResponse joinByInviteCode(String inviteCode, String userUuidValue) {
        long startedAt = System.nanoTime();
        InviteMetadata invite = null;
        String boothType = null;
        AppUser user = null;
        try {
            validateInviteCode(inviteCode);
            user = anonymousUserResolver.resolve(userUuidValue);
            invite = findInvite(inviteCode);
            validateNotExpired(invite);
            boothType = normalizeBoothType(invite.boothType());
            InviteJoinHandler handler = findInviteJoinHandler(boothType);
            StructuredEventLogger.apiBusiness("invite_join_requested", "invite", user.getId().toString(),
                StructuredEventLogger.metadata("invite_code_hash", StructuredEventLogger.sha256Prefix(inviteCode),
                    "booth_type", boothType, "room_id", invite.roomId(), "result", "requested"));

            InviteJoinResponse response = handler.join(invite, user);
            StructuredEventLogger.apiBusiness("invite_join_succeeded", "invite", user.getId().toString(),
                StructuredEventLogger.metadata("invite_code_hash", StructuredEventLogger.sha256Prefix(inviteCode),
                    "booth_type", response.boothType(), "room_id", response.roomId(), "already_joined",
                    response.alreadyJoined(), "role", response.yourRole(), "participant_count",
                    response.currentParticipants(), "duration_ms", calculateDurationMs(startedAt), "result",
                    "success"));

            return response;
        } catch (RuntimeException e) {
            logInviteJoinBlocked(inviteCode, userUuidValue, invite, boothType, e, calculateDurationMs(startedAt));
            throw e;
        }
    }

    /**
     * 초대코드는 Redis 조회 전에 6자리 대문자/숫자 형식인지 먼저 검증합니다.
     */
    private void validateInviteCode(String inviteCode) {
        if (!StringUtils.hasText(inviteCode) || !INVITE_CODE_PATTERN.matcher(inviteCode).matches()) {
            throw new BadRequestException(INVALID_INVITE_CODE_MESSAGE);
        }
    }

    private InviteMetadata findInvite(String inviteCode) {
        return inviteRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new NotFoundException(INVITE_NOT_FOUND_MESSAGE));
    }

    /**
     * Redis TTL만으로는 404와 410을 구분하기 어려우므로 value의 expiresAt을 기준으로 만료를 판단합니다.
     */
    private void validateNotExpired(InviteMetadata invite) {
        if (invite.expiresAt() != null && !invite.expiresAt().isAfter(LocalDateTime.now())) {
            throw new GoneException(INVITE_EXPIRED_MESSAGE);
        }
    }

    private String normalizeBoothType(String boothType) {
        return StringUtils.hasText(boothType) ? boothType.trim().toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 등록된 부스별 입장 핸들러 중 요청 부스 타입을 처리할 구현체를 찾습니다.
     */
    private InviteJoinHandler findInviteJoinHandler(String boothType) {
        for (InviteJoinHandler handler : inviteJoinHandlers) {
            if (handler.supports(boothType)) {
                return handler;
            }
        }

        throw new BadRequestException(UNSUPPORTED_BOOTH_TYPE_MESSAGE);
    }

    private void logInviteJoinBlocked(String inviteCode, String userUuidValue, InviteMetadata invite, String boothType,
        RuntimeException e, long durationMs) {
        StructuredEventLogger.apiBusinessWarn("invite_join_blocked", "invite", safeUuid(userUuidValue),
            "invite join blocked",
            StructuredEventLogger.metadata("invite_code_hash", StructuredEventLogger.sha256Prefix(inviteCode),
                "booth_type", boothType == null && invite != null ? normalizeBoothType(invite.boothType()) : boothType,
                "room_id", invite == null ? null : invite.roomId(), "duration_ms", durationMs, "result", "blocked",
                "reason_code", e.getClass().getSimpleName()),
            e);
    }

    private long calculateDurationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String safeUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
