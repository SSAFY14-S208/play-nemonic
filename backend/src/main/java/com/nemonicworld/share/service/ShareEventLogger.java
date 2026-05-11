package com.nemonicworld.share.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// 공유 생성 로그와 공유 링크 유입 로그와 게임 시작/전환 로그를 서로 연결하는 키
public class ShareEventLogger {

    private static final Logger log = LoggerFactory.getLogger(ShareEventLogger.class);
    private static final String SERVICE_NAME = "api-server";
    private static final String SHARE_LINK_CREATED_EVENT = "share_link_created";

    private final ObjectMapper objectMapper;

    /**
     * 공유 정보 생성 이벤트를 비즈니스 이벤트 로그로 남깁니다.
     */
    public void logShareCreated(String userUuid, String shareToken, GalleryDetailResponse galleryItem,
        String campaign) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("share_token", shareToken);
        metadata.put("gallery_id", galleryItem.galleryId());
        metadata.put("artifact_id", galleryItem.artifactId());
        metadata.put("artifact_kind", galleryItem.kind());
        metadata.put("utm_campaign", campaign);

        emit(userUuid, galleryItem.kind(), SHARE_LINK_CREATED_EVENT, "sns share info created", metadata);
    }

    /**
     * artifact ID 기반 공유 정보 생성 이벤트를 비즈니스 이벤트 로그로 남깁니다.
     */
    public void logArtifactShareCreated(String userUuid, String shareToken, UUID artifactId, String artifactKind,
        String campaign) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("share_token", shareToken);
        metadata.put("artifact_id", artifactId);
        metadata.put("artifact_kind", artifactKind);
        metadata.put("utm_campaign", campaign);

        emit(userUuid, artifactKind, SHARE_LINK_CREATED_EVENT, "artifact sns share info created", metadata);
    }

    private void emit(String userUuid, String contentType, String eventName, String message,
        Map<String, Object> metadata) {
        Map<String, Object> eventLog = new LinkedHashMap<>();
        eventLog.put("@timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        eventLog.put("level", "INFO");
        eventLog.put("service", SERVICE_NAME);
        eventLog.put("trace_id", UUID.randomUUID().toString());
        eventLog.put("uuid", userUuid);
        eventLog.put("event_name", eventName);
        eventLog.put("content_type", contentType);
        eventLog.put("message", message);
        eventLog.put("metadata", metadata);

        try {
            System.out.println(objectMapper.writeValueAsString(eventLog));
        } catch (JsonProcessingException e) {
            log.error("business_log_emit_failure event_name={}", eventName, e);
        }
    }
}
