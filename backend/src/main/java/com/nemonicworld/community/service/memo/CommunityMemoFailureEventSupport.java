package com.nemonicworld.community.service.memo;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

import static com.nemonicworld.community.service.support.CommunityMemoEventLogger.metadata;

@Component
class CommunityMemoFailureEventSupport {

    private final CommunityMemoRepository communityMemoRepository;
    private final CommunityMemoUserSupport communityMemoUserSupport;

    CommunityMemoFailureEventSupport(CommunityMemoRepository communityMemoRepository,
        CommunityMemoUserSupport communityMemoUserSupport) {
        this.communityMemoRepository = communityMemoRepository;
        this.communityMemoUserSupport = communityMemoUserSupport;
    }

    CommunityMemoDetailRow findVisibleMemoOrLogNotFound(UUID memoId, UUID actorUuid, String eventName) {
        return findVisibleMemoOrLogNotFound(memoId, actorUuid, eventName, -1L);
    }

    CommunityMemoDetailRow findVisibleMemoOrLogNotFound(UUID memoId, UUID actorUuid, String eventName, long startedAt) {
        return communityMemoRepository.findVisibleMemoById(memoId).orElseThrow(() -> {
            String reasonCode = resolveNotVisibleReasonCode(memoId);
            Map<String, Object> eventMetadata = metadata("memo_id", memoId, "reason_code", reasonCode, "status",
                "not_found");
            if (startedAt >= 0) {
                eventMetadata.put("duration_ms", calculateLatencyMs(startedAt));
            }
            CommunityMemoEventLogger.business(eventName, actorUuid, eventMetadata);
            logHiddenOrDeletedAccessAttempt(actorUuid, eventMetadata);
            return new NotFoundException(CommunityMemoSupport.COMMUNITY_MEMO_NOT_FOUND_MESSAGE);
        });
    }

    void logCreateValidationFailed(String userUuidValue, CommunityMemoCreateRequest request, RuntimeException error,
        String moderationBlockedMessage, String moderationUnavailableMessage) {
        if (moderationBlockedMessage.equals(error.getMessage())
            || moderationUnavailableMessage.equals(error.getMessage())) {
            return;
        }

        UUID userUuid = communityMemoUserSupport.parseUuidQuietly(userUuidValue);
        CommunityMemoEventLogger.business("community_memo_create_validation_failed", userUuid,
            metadata("source_type", request == null ? null : request.sourceType(), "original_file_id",
                request == null ? null : request.originalFileId(), "thumbnail_file_id",
                request == null ? null : request.thumbnailFileId(), "source_gallery_id",
                request == null ? null : request.sourceGalleryId(), "reason_code", exceptionReasonCode(error),
                "message", error.getMessage()));
        logCreateSecurityEvent(userUuid, request, error);
    }

    void logCommunityActionFailed(String eventName, String memoIdValue, String userUuidValue, RuntimeException error) {
        UUID actorUuid = communityMemoUserSupport.parseUuidQuietly(userUuidValue);
        Map<String, Object> eventMetadata = metadata("memo_id", memoIdValue, "reason_code", exceptionReasonCode(error),
            "message", error.getMessage());
        if (memoIdValue != null) {
            UUID memoId = communityMemoUserSupport.parseUuidQuietly(memoIdValue);
            if (memoId != null) {
                eventMetadata.put("visibility_reason_code", resolveNotVisibleReasonCode(memoId));
            }
        }
        CommunityMemoEventLogger.business(eventName, actorUuid, eventMetadata);
        logCommunitySecurityEvent(eventName, actorUuid, eventMetadata, error);
    }

    long calculateLatencyMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String resolveNotVisibleReasonCode(UUID memoId) {
        return communityMemoRepository.findMemoVisibilityById(memoId).map(row -> {
            if (row.deletedAt() != null) {
                return "deleted";
            }
            if (row.hidden()) {
                return "hidden";
            }

            return "not_visible";
        }).orElse("not_found");
    }

    private void logCreateSecurityEvent(UUID actorUuid, CommunityMemoCreateRequest request, RuntimeException error) {
        if (error instanceof ForbiddenException) {
            CommunityMemoEventLogger
                .warn("community_file_ownership_violation", "community file ownership violation", actorUuid,
                    metadata("original_file_id", request == null ? null : request.originalFileId(), "thumbnail_file_id",
                        request == null ? null : request.thumbnailFileId(), "reason_code", "file_owner_mismatch"),
                    error);
            return;
        }

        if (error instanceof NotFoundException && "존재하지 않는 사용자입니다.".equals(error.getMessage())) {
            CommunityMemoEventLogger.warn("community_user_not_found", "community user not found", actorUuid,
                metadata("reason_code", "user_not_found"), error);
        }
    }

    private void logCommunitySecurityEvent(String eventName, UUID actorUuid, Map<String, Object> eventMetadata,
        RuntimeException error) {
        if (error instanceof ForbiddenException) {
            CommunityMemoEventLogger.warn("community_ownership_violation", "community ownership violation", actorUuid,
                eventMetadata, error);
            return;
        }

        if (error instanceof ConflictException && "community_memo_report_rejected".equals(eventName)) {
            CommunityMemoEventLogger.warn("community_duplicate_report_attempt", "community duplicate report attempt",
                actorUuid, eventMetadata, error);
            return;
        }

        if (error instanceof NotFoundException) {
            logHiddenOrDeletedAccessAttempt(actorUuid, eventMetadata);
            if ("존재하지 않는 사용자입니다.".equals(error.getMessage())) {
                CommunityMemoEventLogger.warn("community_user_not_found", "community user not found", actorUuid,
                    eventMetadata, error);
            }
        }
    }

    private void logHiddenOrDeletedAccessAttempt(UUID actorUuid, Map<String, Object> eventMetadata) {
        Object reasonCode = eventMetadata.get("visibility_reason_code");
        if (reasonCode == null) {
            reasonCode = eventMetadata.get("reason_code");
        }

        if ("hidden".equals(reasonCode)) {
            CommunityMemoEventLogger.warn("community_hidden_memo_access_attempt",
                "community hidden memo access attempt", actorUuid, eventMetadata, null);
            return;
        }

        if ("deleted".equals(reasonCode)) {
            CommunityMemoEventLogger.warn("community_deleted_memo_access_attempt",
                "community deleted memo access attempt", actorUuid, eventMetadata, null);
        }
    }

    private String exceptionReasonCode(RuntimeException error) {
        if (error instanceof BadRequestException) {
            return "bad_request";
        }
        if (error instanceof ForbiddenException) {
            return "forbidden";
        }
        if (error instanceof NotFoundException) {
            return "not_found";
        }
        if (error instanceof ConflictException) {
            return "conflict";
        }

        return error.getClass().getSimpleName();
    }
}
