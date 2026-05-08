package com.nemonicworld.relay.service.finalization;

import java.util.UUID;

/**
 * canvasIndex 하나에서 생성된 최종 릴레이 결과물 정보를 담습니다.
 */
public record RelayFinalizationArtifactResult(UUID artifactId, int canvasIndex, String originalObjectKey,
    String thumbnailObjectKey, String meta) {
}
