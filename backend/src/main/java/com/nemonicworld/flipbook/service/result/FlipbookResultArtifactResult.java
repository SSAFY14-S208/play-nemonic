package com.nemonicworld.flipbook.service.result;

import java.util.UUID;

/**
 * flipbookIndex 하나에서 생성된 최종 GIF 결과물 정보를 담습니다.
 */
public record FlipbookResultArtifactResult(UUID artifactId, int flipbookIndex, String gifObjectKey,
    String firstImageObjectKey, String thumbnailObjectKey, String meta) {
}
