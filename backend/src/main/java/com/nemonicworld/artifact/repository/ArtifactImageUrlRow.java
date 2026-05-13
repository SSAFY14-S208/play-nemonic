package com.nemonicworld.artifact.repository;

import java.util.UUID;

/**
 * 산출물 공통 행과 하위 타입별 이미지 객체 키를 함께 담는 조회 모델입니다.
 */
public record ArtifactImageUrlRow(UUID artifactId, String kind, String thumbnailUrl, String fortuneImageUrl,
    String relayCombinedPreviewUrl, String flipbookGifUrl, String flipbookFirstImageUrl, String infiniteCanvasImageUrl,
    String phoneImageUrl, String communityMemoOriginalImageUrl, String communityMemoThumbnailImageUrl) {
}
