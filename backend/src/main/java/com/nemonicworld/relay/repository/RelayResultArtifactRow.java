package com.nemonicworld.relay.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 릴레이 결과 조회 API가 gallery 소유권 기준으로 읽는 artifact row입니다.
 */
public record RelayResultArtifactRow(UUID galleryId, UUID artifactId, String thumbnailUrl, String contentUrl,
    String meta, LocalDateTime createdAt) {
}
