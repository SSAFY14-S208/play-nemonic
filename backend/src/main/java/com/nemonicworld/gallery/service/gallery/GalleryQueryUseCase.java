package com.nemonicworld.gallery.service.gallery;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.gallery.dto.response.GalleryItemResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;
import com.nemonicworld.gallery.repository.GalleryItemRow;
import com.nemonicworld.gallery.repository.GalleryRepository;
import com.nemonicworld.gallery.repository.GalleryRepository.GalleryDetailRow;
import com.nemonicworld.gallery.service.support.GalleryMetaSupport;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GalleryQueryUseCase {

    private static final String INVALID_GALLERY_ID_MESSAGE = "유효하지 않은 갤러리 항목 ID 형식입니다.";
    private static final String GALLERY_ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 갤러리 항목입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final GalleryRepository galleryRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final GalleryMetaSupport galleryMetaSupport;

    public GalleryQueryUseCase(GalleryRepository galleryRepository, AnonymousUserResolver anonymousUserResolver,
        MinioPublicUrlResolver minioPublicUrlResolver, GalleryMetaSupport galleryMetaSupport) {
        this.galleryRepository = galleryRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.galleryMetaSupport = galleryMetaSupport;
    }

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
        StructuredEventLogger.apiBusiness("gallery_list_viewed", "gallery", userUuid.toString(),
            StructuredEventLogger.metadata("page", page, "size", size, "item_count", items.size(), "total_elements",
                totalElements, "result", "success"));

        return new GalleryListResponse(items, page, size, totalElements, calculateHasNext(page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public GalleryDetailResponse getMyGalleryItemDetail(String userUuidValue, String galleryIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID galleryId = parseGalleryId(galleryIdValue);

        anonymousUserResolver.resolve(userUuid);

        GalleryDetailRow row = galleryRepository.findActiveItemDetail(galleryId, userUuid)
            .orElseThrow(() -> new NotFoundException(GALLERY_ITEM_NOT_FOUND_MESSAGE));
        StructuredEventLogger.apiBusiness("gallery_detail_viewed", "gallery", userUuid.toString(),
            StructuredEventLogger.metadata("gallery_id", row.galleryId(), "artifact_id", row.artifactId(), "kind",
                row.kind(), "result", "success"));

        return new GalleryDetailResponse(row.galleryId().toString(), row.artifactId().toString(), row.kind(),
            minioPublicUrlResolver.resolve(row.thumbnailUrl()), minioPublicUrlResolver.resolve(row.contentUrl()),
            row.sourceRoomId(), galleryMetaSupport.parseGalleryMeta(row.meta()), row.createdAt(), row.updatedAt());
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
            minioPublicUrlResolver.resolve(row.thumbnailUrl()), minioPublicUrlResolver.resolve(row.contentUrl()),
            row.sourceRoomId(), row.createdAt());
    }
}
