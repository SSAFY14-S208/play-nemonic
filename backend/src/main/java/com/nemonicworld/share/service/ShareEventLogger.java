package com.nemonicworld.share.service;

import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.global.logging.StructuredEventLogger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ShareEventLogger {

    private static final String SHARE_LINK_CREATED_EVENT = "share_link_created";
    private static final String SHARE_LINK_CREATE_FAILED_EVENT = "share_link_create_failed";

    public void logShareCreated(String userUuid, String shareToken, GalleryDetailResponse galleryItem,
        String campaign) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("share_token_hash", StructuredEventLogger.sha256Prefix(shareToken));
        metadata.put("gallery_id", galleryItem.galleryId());
        metadata.put("artifact_id", galleryItem.artifactId());
        metadata.put("artifact_kind", galleryItem.kind());
        metadata.put("utm_campaign", campaign);
        metadata.put("result", "success");

        emit(userUuid, galleryItem.kind(), SHARE_LINK_CREATED_EVENT, metadata);
    }

    public void logArtifactShareCreated(String userUuid, String shareToken, UUID artifactId, String artifactKind,
        String campaign) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("share_token_hash", StructuredEventLogger.sha256Prefix(shareToken));
        metadata.put("artifact_id", artifactId);
        metadata.put("artifact_kind", artifactKind);
        metadata.put("utm_campaign", campaign);
        metadata.put("result", "success");

        emit(userUuid, artifactKind, SHARE_LINK_CREATED_EVENT, metadata);
    }

    public void logCommunityMemoShareCreated(String userUuid, String shareToken, UUID memoId, String campaign) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("share_token_hash", StructuredEventLogger.sha256Prefix(shareToken));
        metadata.put("memo_id", memoId);
        metadata.put("artifact_kind", "community_memo");
        metadata.put("utm_campaign", campaign);
        metadata.put("result", "success");

        emit(userUuid, "community_memo", SHARE_LINK_CREATED_EVENT, metadata);
    }

    public void logShareCreateFailed(String userUuid, String galleryId, Throwable error) {
        Map<String, Object> metadata = StructuredEventLogger.metadata("gallery_id", galleryId, "result", "failed",
            "reason_code", error.getClass().getSimpleName());

        StructuredEventLogger.apiBusinessWarn(SHARE_LINK_CREATE_FAILED_EVENT, "share", safeUuid(userUuid),
            "share link create failed", metadata, error);
    }

    public void logArtifactShareCreateFailed(String userUuid, String artifactId, Throwable error) {
        Map<String, Object> metadata = StructuredEventLogger.metadata("artifact_id", artifactId, "result", "failed",
            "reason_code", error.getClass().getSimpleName());

        StructuredEventLogger.apiBusinessWarn(SHARE_LINK_CREATE_FAILED_EVENT, "share", safeUuid(userUuid),
            "artifact share link create failed", metadata, error);
    }

    public void logCommunityMemoShareCreateFailed(String userUuid, String memoId, Throwable error) {
        Map<String, Object> metadata = StructuredEventLogger.metadata("memo_id", memoId, "result", "failed",
            "reason_code", error.getClass().getSimpleName());

        StructuredEventLogger.apiBusinessWarn(SHARE_LINK_CREATE_FAILED_EVENT, "share", safeUuid(userUuid),
            "community memo share link create failed", metadata, error);
    }

    private void emit(String userUuid, String contentType, String eventName, Map<String, Object> metadata) {
        StructuredEventLogger.apiBusiness(eventName, contentType, safeUuid(userUuid), metadata);
    }

    private String safeUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
