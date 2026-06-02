package com.nemonicworld.gallery.service.gallery;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.gallery.dto.response.GalleryDeleteResponse;
import com.nemonicworld.gallery.repository.GalleryRepository;
import com.nemonicworld.gallery.repository.GalleryRepository.GalleryDeleteTargetRow;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GalleryCommandUseCase {

    private static final String INVALID_GALLERY_ID_MESSAGE = "유효하지 않은 갤러리 항목 ID 형식입니다.";
    private static final String GALLERY_ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 갤러리 항목입니다.";

    private final GalleryRepository galleryRepository;
    private final AnonymousUserResolver anonymousUserResolver;

    public GalleryCommandUseCase(GalleryRepository galleryRepository, AnonymousUserResolver anonymousUserResolver) {
        this.galleryRepository = galleryRepository;
        this.anonymousUserResolver = anonymousUserResolver;
    }

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
        StructuredEventLogger.apiBusiness("gallery_item_deleted", "gallery", userUuid.toString(), StructuredEventLogger
            .metadata("gallery_id", target.galleryId(), "artifact_id", target.artifactId(), "result", "success"));

        return new GalleryDeleteResponse(target.galleryId().toString(), target.artifactId().toString(), deletedAt);
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
}
