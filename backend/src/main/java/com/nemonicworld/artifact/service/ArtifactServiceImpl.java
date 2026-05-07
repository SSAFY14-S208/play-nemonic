package com.nemonicworld.artifact.service;

import com.nemonicworld.artifact.dto.response.ArtifactContentUrlResponse;
import com.nemonicworld.artifact.dto.response.ArtifactImageUrlResponse;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRepository;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRow;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.files.service.MinioPublicUrlResolver;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * artifact ID를 기준으로 산출물 이미지 URL 조회 유스케이스를 처리합니다.
 */
@Service
public class ArtifactServiceImpl implements ArtifactService {

    private static final String INVALID_ARTIFACT_ID_MESSAGE = "유효하지 않은 산출물 ID 형식입니다.";
    private static final String ARTIFACT_IMAGE_URL_NOT_FOUND_MESSAGE = "조회 가능한 산출물 이미지 URL을 찾을 수 없습니다.";
    private static final String KIND_FORTUNE = "fortune";
    private static final String KIND_RELAY_DRAWING = "relay_drawing";
    private static final String KIND_FLIPBOOK = "flipbook";
    private static final String KIND_INFINITE_CANVAS = "infinite_canvas";
    private static final String KIND_PHONE = "phone";
    private static final String KIND_COMMUNITY_MEMO = "community_memo";
    private static final String CONTENT_TYPE_FORTUNE_IMAGE = "fortune_image";
    private static final String CONTENT_TYPE_COMBINED_PREVIEW = "combined_preview";
    private static final String CONTENT_TYPE_GIF = "gif";
    private static final String CONTENT_TYPE_FIRST_IMAGE = "first_image";
    private static final String CONTENT_TYPE_CANVAS_IMAGE = "canvas_image";
    private static final String CONTENT_TYPE_PHONE_IMAGE = "phone_image";
    private static final String CONTENT_TYPE_COMMUNITY_MEMO_IMAGE = "community_memo_image";
    private static final String CONTENT_TYPE_THUMBNAIL = "thumbnail";

    private final ArtifactImageUrlRepository artifactImageUrlRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public ArtifactServiceImpl(ArtifactImageUrlRepository artifactImageUrlRepository,
        AnonymousUserResolver anonymousUserResolver, MinioPublicUrlResolver minioPublicUrlResolver) {
        this.artifactImageUrlRepository = artifactImageUrlRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    /**
     * 기존 사용자와 active gallery row를 확인한 뒤 DB의 object key를 public URL로 변환합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public ArtifactImageUrlResponse getArtifactImageUrls(String userUuidValue, String artifactIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID artifactId = parseArtifactId(artifactIdValue);

        anonymousUserResolver.resolve(userUuid);

        ArtifactImageUrlRow row = artifactImageUrlRepository.findActiveArtifactImageUrl(artifactId, userUuid)
            .orElseThrow(() -> new NotFoundException(ARTIFACT_IMAGE_URL_NOT_FOUND_MESSAGE));

        String thumbnailUrl = minioPublicUrlResolver.resolve(row.thumbnailUrl());
        List<ArtifactContentUrlResponse> contents = createContents(row);
        if (!StringUtils.hasText(thumbnailUrl) && contents.isEmpty()) {
            throw new NotFoundException(ARTIFACT_IMAGE_URL_NOT_FOUND_MESSAGE);
        }

        return new ArtifactImageUrlResponse(row.artifactId().toString(), row.kind(), thumbnailUrl, contents);
    }

    private UUID parseArtifactId(String artifactIdValue) {
        if (!StringUtils.hasText(artifactIdValue)) {
            throw new BadRequestException(INVALID_ARTIFACT_ID_MESSAGE);
        }

        try {
            return UUID.fromString(artifactIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_ARTIFACT_ID_MESSAGE);
        }
    }

    private List<ArtifactContentUrlResponse> createContents(ArtifactImageUrlRow row) {
        List<ArtifactContentUrlResponse> contents = new ArrayList<>();
        String kind = row.kind();

        if (KIND_FORTUNE.equals(kind)) {
            addContent(contents, CONTENT_TYPE_FORTUNE_IMAGE, firstText(row.fortuneImageUrl(), row.thumbnailUrl()));
        } else if (KIND_RELAY_DRAWING.equals(kind)) {
            addContent(contents, CONTENT_TYPE_COMBINED_PREVIEW,
                firstText(row.relayCombinedPreviewUrl(), row.thumbnailUrl()));
        } else if (KIND_FLIPBOOK.equals(kind)) {
            addContent(contents, CONTENT_TYPE_GIF, row.flipbookGifUrl());
            addContent(contents, CONTENT_TYPE_FIRST_IMAGE, row.flipbookFirstImageUrl());
        } else if (KIND_INFINITE_CANVAS.equals(kind)) {
            addContent(contents, CONTENT_TYPE_CANVAS_IMAGE,
                firstText(row.infiniteCanvasImageUrl(), row.thumbnailUrl()));
        } else if (KIND_PHONE.equals(kind)) {
            addContent(contents, CONTENT_TYPE_PHONE_IMAGE, firstText(row.phoneImageUrl(), row.thumbnailUrl()));
        } else if (KIND_COMMUNITY_MEMO.equals(kind)) {
            addContent(contents, CONTENT_TYPE_COMMUNITY_MEMO_IMAGE, row.thumbnailUrl());
        }

        if (contents.isEmpty()) {
            addContent(contents, CONTENT_TYPE_THUMBNAIL, row.thumbnailUrl());
        }

        return contents;
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private void addContent(List<ArtifactContentUrlResponse> contents, String type, String objectReference) {
        String url = minioPublicUrlResolver.resolve(objectReference);
        if (StringUtils.hasText(url)) {
            contents.add(new ArtifactContentUrlResponse(type, url));
        }
    }
}
