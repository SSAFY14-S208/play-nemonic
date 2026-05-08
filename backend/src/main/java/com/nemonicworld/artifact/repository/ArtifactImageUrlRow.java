package com.nemonicworld.artifact.repository;

import java.util.UUID;

/**
 * artifact 공통 row와 subtype별 이미지 object key를 함께 담는 조회 모델입니다.
 */
public record ArtifactImageUrlRow(UUID artifactId, String kind, String thumbnailUrl, String fortuneImageUrl,
    String relayCombinedPreviewUrl, String flipbookGifUrl, String flipbookFirstImageUrl, String infiniteCanvasImageUrl,
    String phoneImageUrl) {
}
