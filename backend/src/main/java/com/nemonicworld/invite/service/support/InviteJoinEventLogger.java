package com.nemonicworld.invite.service.support;

import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.invite.dto.response.InviteJoinResponse;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.user.entity.AppUser;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InviteJoinEventLogger {

    private static final String CONTENT_TYPE = "invite";
    private static final String INVITE_JOIN_REQUESTED_EVENT = "invite_join_requested";
    private static final String INVITE_JOIN_SUCCEEDED_EVENT = "invite_join_succeeded";
    private static final String INVITE_JOIN_BLOCKED_EVENT = "invite_join_blocked";

    public long startTimer() {
        return System.nanoTime();
    }

    public void logRequested(String inviteCode, AppUser user, InviteMetadata invite, String boothType) {
        StructuredEventLogger.apiBusiness(INVITE_JOIN_REQUESTED_EVENT, CONTENT_TYPE, user.getId().toString(),
            StructuredEventLogger.metadata("invite_code_hash", StructuredEventLogger.sha256Prefix(inviteCode),
                "booth_type", boothType, "room_id", invite.roomId(), "result", "requested"));
    }

    public void logSucceeded(String inviteCode, AppUser user, InviteJoinResponse response, long startedAt) {
        StructuredEventLogger.apiBusiness(INVITE_JOIN_SUCCEEDED_EVENT, CONTENT_TYPE, user.getId().toString(),
            StructuredEventLogger.metadata("invite_code_hash", StructuredEventLogger.sha256Prefix(inviteCode),
                "booth_type", response.boothType(), "room_id", response.roomId(), "already_joined",
                response.alreadyJoined(), "role", response.yourRole(), "participant_count",
                response.currentParticipants(), "duration_ms", calculateDurationMs(startedAt), "result", "success"));
    }

    public void logBlocked(String inviteCode, String userUuidValue, InviteMetadata invite, String boothType,
        RuntimeException error, long startedAt) {
        StructuredEventLogger.apiBusinessWarn(INVITE_JOIN_BLOCKED_EVENT, CONTENT_TYPE, safeUuid(userUuidValue),
            "invite join blocked",
            StructuredEventLogger.metadata("invite_code_hash", StructuredEventLogger.sha256Prefix(inviteCode),
                "booth_type", boothType, "room_id", invite == null ? null : invite.roomId(), "duration_ms",
                calculateDurationMs(startedAt), "result", "blocked", "reason_code", error.getClass().getSimpleName()),
            error);
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
