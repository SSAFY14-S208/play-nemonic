package com.nemonicworld.gallery.phone.service.drawing;

import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.gallery.phone.dto.request.PhoneDrawingSaveRequest;
import com.nemonicworld.gallery.phone.dto.response.PhoneDrawingSaveResponse;
import com.nemonicworld.gallery.phone.entity.PhoneDrawingArtifact;
import com.nemonicworld.gallery.phone.repository.PhoneDrawingCreateCommand;
import com.nemonicworld.gallery.phone.repository.PhoneDrawingRepository;
import com.nemonicworld.gallery.phone.service.support.PhoneDrawingFileSupport;
import com.nemonicworld.gallery.service.support.GalleryMetaSupport;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PhoneDrawingSaveUseCase {

    private static final String ARTIFACT_KIND_PHONE = "phone";

    private final AnonymousUserResolver anonymousUserResolver;
    private final PhoneDrawingRepository phoneDrawingRepository;
    private final PhoneDrawingFileSupport phoneDrawingFileSupport;
    private final GalleryMetaSupport galleryMetaSupport;
    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public PhoneDrawingSaveUseCase(AnonymousUserResolver anonymousUserResolver,
        PhoneDrawingRepository phoneDrawingRepository, PhoneDrawingFileSupport phoneDrawingFileSupport,
        GalleryMetaSupport galleryMetaSupport, MinioPublicUrlResolver minioPublicUrlResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.phoneDrawingRepository = phoneDrawingRepository;
        this.phoneDrawingFileSupport = phoneDrawingFileSupport;
        this.galleryMetaSupport = galleryMetaSupport;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    @Transactional
    public PhoneDrawingSaveResponse savePhoneDrawing(String userUuidValue, PhoneDrawingSaveRequest request) {
        try {
            return savePhoneDrawingInternal(userUuidValue, request);
        } catch (RuntimeException e) {
            logPhoneDrawingSaveFailed(userUuidValue, request, e);
            throw e;
        }
    }

    private PhoneDrawingSaveResponse savePhoneDrawingInternal(String userUuidValue, PhoneDrawingSaveRequest request) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        anonymousUserResolver.resolve(userUuid);

        UUID imageFileId = phoneDrawingFileSupport
            .parseRequiredImageFileId(request == null ? null : request.imageFileId());
        UUID thumbnailFileId = phoneDrawingFileSupport
            .parseOptionalThumbnailFileId(request == null ? null : request.thumbnailFileId());
        String meta = galleryMetaSupport.serializePhoneDrawingMeta(request == null ? null : request.meta());
        StructuredEventLogger.apiBusiness("phone_drawing_save_requested", ARTIFACT_KIND_PHONE, userUuid.toString(),
            StructuredEventLogger.metadata("image_file_id", imageFileId, "thumbnail_file_id", thumbnailFileId, "result",
                "requested"));

        FileUpload imageFileUpload = phoneDrawingFileSupport.findFileUpload(imageFileId);
        phoneDrawingFileSupport.validatePhoneFile(imageFileUpload, userUuid);

        FileUpload thumbnailFileUpload = null;
        if (thumbnailFileId != null) {
            thumbnailFileUpload = phoneDrawingFileSupport.findFileUpload(thumbnailFileId);
            phoneDrawingFileSupport.validatePhoneFile(thumbnailFileUpload, userUuid);
        }

        String imageObjectKey = imageFileUpload.getObjectKey();
        String thumbnailObjectKey = thumbnailFileUpload == null ? imageObjectKey : thumbnailFileUpload.getObjectKey();
        LocalDateTime now = LocalDateTime.now();
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();

        phoneDrawingRepository.save(new PhoneDrawingCreateCommand(galleryId, artifactId, userUuid, ARTIFACT_KIND_PHONE,
            imageObjectKey, thumbnailObjectKey, meta, now, now));

        PhoneDrawingArtifact artifact = new PhoneDrawingArtifact(galleryId, artifactId, ARTIFACT_KIND_PHONE,
            thumbnailObjectKey, imageObjectKey, now);
        StructuredEventLogger.apiBusiness("phone_drawing_saved", ARTIFACT_KIND_PHONE, userUuid.toString(),
            StructuredEventLogger.metadata("gallery_id", galleryId, "artifact_id", artifactId, "image_file_id",
                imageFileId, "thumbnail_file_id", thumbnailFileId, "result", "success"));
        return PhoneDrawingSaveResponse.from(artifact, minioPublicUrlResolver.resolve(thumbnailObjectKey),
            minioPublicUrlResolver.resolve(imageObjectKey));
    }

    private void logPhoneDrawingSaveFailed(String userUuidValue, PhoneDrawingSaveRequest request, RuntimeException e) {
        StructuredEventLogger.apiBusinessWarn("phone_drawing_save_failed", ARTIFACT_KIND_PHONE, safeUuid(userUuidValue),
            "phone drawing save failed",
            StructuredEventLogger.metadata("image_file_id", request == null ? null : request.imageFileId(),
                "thumbnail_file_id", request == null ? null : request.thumbnailFileId(), "result", "failed",
                "reason_code", e.getClass().getSimpleName()),
            e);
    }

    private String safeUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
