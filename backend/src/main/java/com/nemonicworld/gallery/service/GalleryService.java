package com.nemonicworld.gallery.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.gallery.dto.response.GalleryItemResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;
import com.nemonicworld.gallery.repository.GalleryItemRow;
import com.nemonicworld.gallery.repository.GalleryRepository;
import com.nemonicworld.user.repository.UserRepository;
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

    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";
    private static final String INVALID_PAGE_REQUEST_MESSAGE = "페이지 요청 값이 올바르지 않습니다.";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final GalleryRepository galleryRepository;
    private final UserRepository userRepository;

    public GalleryService(GalleryRepository galleryRepository, UserRepository userRepository) {
        this.galleryRepository = galleryRepository;
        this.userRepository = userRepository;
    }

    /**
     * 존재하는 익명 사용자의 보관 결과물을 최신순으로 조회합니다.
     */
    @Transactional(readOnly = true)
    public GalleryListResponse getMyGallery(String userUuidValue, String pageValue, String sizeValue) {
        UUID userUuid = parseUserUuid(userUuidValue);
        int page = parsePage(pageValue);
        int size = parseSize(sizeValue);

        if (!userRepository.existsById(userUuid)) {
            throw new NotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        long totalElements = galleryRepository.countActiveItemsByUserId(userUuid);
        List<GalleryItemResponse> items = galleryRepository
            .findActiveItemsByUserId(userUuid, size, calculateOffset(page, size)).stream().map(this::toResponse)
            .toList();

        return new GalleryListResponse(items, page, size, totalElements, calculateHasNext(page, size, totalElements));
    }

    private UUID parseUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        try {
            return UUID.fromString(userUuid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
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
