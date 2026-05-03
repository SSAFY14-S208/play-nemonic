package com.nemonicworld.gallery.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.gallery.dto.response.GalleryDeleteResponse;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.gallery.dto.response.GalleryItemResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;
import com.nemonicworld.gallery.repository.GalleryItemRow;
import com.nemonicworld.gallery.repository.GalleryRepository;
import com.nemonicworld.gallery.repository.GalleryRepository.GalleryDetailRow;
import com.nemonicworld.gallery.repository.GalleryRepository.GalleryDeleteTargetRow;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
/**
 * UUID 기반 내 갤러리 조회 유스케이스를 처리하는 서비스입니다.
 */
public class GalleryService {

    private static final String INVALID_GALLERY_ID_MESSAGE = "유효하지 않은 갤러리 항목 ID 형식입니다.";
    private static final String GALLERY_ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 갤러리 항목입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() {
    };

    private final GalleryRepository galleryRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final ObjectMapper objectMapper;

    public GalleryService(GalleryRepository galleryRepository, AnonymousUserResolver anonymousUserResolver,
        ObjectMapper objectMapper) {
        this.galleryRepository = galleryRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 존재하는 익명 사용자의 보관 결과물을 최신순으로 조회합니다.
     */
    @Transactional(readOnly = true)
    public GalleryListResponse getMyGallery(String userUuidValue, String pageValue, String sizeValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);

        anonymousUserResolver.resolve(userUuid);

        long totalElements = galleryRepository.countActiveItemsByUserId(userUuid);
        List<GalleryItemResponse> items = galleryRepository
            .findActiveItemsByUserId(userUuid, size, calculateOffset(page, size)).stream().map(this::toResponse)
            .toList();

        return new GalleryListResponse(items, page, size, totalElements, calculateHasNext(page, size, totalElements));
    }

    /**
     * 존재하는 익명 사용자의 보관 결과물 한 건을 상세 조회합니다.
     */
    @Transactional(readOnly = true)
    public GalleryDetailResponse getMyGalleryItemDetail(String userUuidValue, String galleryIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID galleryId = parseGalleryId(galleryIdValue);

        anonymousUserResolver.resolve(userUuid);

        GalleryDetailRow row = galleryRepository.findActiveItemDetail(galleryId, userUuid)
            .orElseThrow(() -> new NotFoundException(GALLERY_ITEM_NOT_FOUND_MESSAGE));

        return new GalleryDetailResponse(row.galleryId().toString(), row.artifactId().toString(), row.kind(),
            row.thumbnailUrl(), row.contentUrl(), row.sourceRoomId(), parseMeta(row.meta()), row.createdAt(),
            row.updatedAt());
    }

    /**
     * 원본 artifact는 보존하고 갤러리 보관 관계만 soft delete 처리합니다.
     */
    @Transactional
    public GalleryDeleteResponse deleteMyGalleryItem(String userUuidValue, String galleryIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID galleryId = parseGalleryId(galleryIdValue);

        anonymousUserResolver.resolve(userUuid);

        GalleryDeleteTargetRow target = galleryRepository.findActiveDeleteTarget(galleryId, userUuid)
            .orElseThrow(() -> new NotFoundException(GALLERY_ITEM_NOT_FOUND_MESSAGE));
        LocalDateTime deletedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int updatedCount = galleryRepository.softDeleteGalleryItem(galleryId, userUuid, deletedAt);

        if (updatedCount == 0) {
            throw new NotFoundException(GALLERY_ITEM_NOT_FOUND_MESSAGE);
        }

        return new GalleryDeleteResponse(target.galleryId().toString(), target.artifactId().toString(), deletedAt);
    }

    private Map<String, Object> parseMeta(String meta) {
        if (!StringUtils.hasText(meta)) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedMeta = objectMapper.readValue(meta, META_TYPE);
            return parsedMeta == null ? Map.of() : parsedMeta;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private UUID parseGalleryId(String galleryId) {
        return parseUuid(galleryId, INVALID_GALLERY_ID_MESSAGE);
    }

    private UUID parseUuid(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(message);
        }
    }

    private int parsePage(String pageValue) {
        int page = parseIntegerOrDefault(pageValue, DEFAULT_PAGE);
        if (page < 0) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return page;
    }

    private int parseSize(String sizeValue) {
        int size = parseIntegerOrDefault(sizeValue, DEFAULT_SIZE);
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }

        return size;
    }

    private int parseIntegerOrDefault(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException(INVALID_PAGE_REQUEST_MESSAGE);
        }
    }

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private boolean calculateHasNext(int page, int size, long totalElements) {
        return calculateOffset(page + 1, size) < totalElements;
    }

    private GalleryItemResponse toResponse(GalleryItemRow row) {
        return new GalleryItemResponse(row.galleryId().toString(), row.artifactId().toString(), row.kind(),
            row.thumbnailUrl(), row.contentUrl(), row.sourceRoomId(), row.createdAt());
    }
}
