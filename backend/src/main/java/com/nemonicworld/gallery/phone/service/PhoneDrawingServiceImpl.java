package com.nemonicworld.gallery.phone.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.gallery.phone.dto.request.PhoneDrawingSaveRequest;
import com.nemonicworld.gallery.phone.dto.response.PhoneDrawingSaveResponse;
import com.nemonicworld.gallery.phone.entity.PhoneDrawingArtifact;
import com.nemonicworld.gallery.phone.repository.PhoneDrawingCreateCommand;
import com.nemonicworld.gallery.phone.repository.PhoneDrawingRepository;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PhoneDrawingServiceImpl implements PhoneDrawingService {

    private static final String ARTIFACT_KIND_PHONE = "phone";
    private static final String INVALID_IMAGE_FILE_ID_MESSAGE = "유효하지 않은 imageFileId 형식입니다.";
    private static final String INVALID_THUMBNAIL_FILE_ID_MESSAGE = "유효하지 않은 thumbnailFileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String INVALID_PHONE_FILE_MESSAGE = "휴대폰 그림 파일만 저장할 수 있습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    private static final String INVALID_META_MESSAGE = "메타데이터 형식이 올바르지 않습니다.";
    private static final String EMPTY_META_JSON = "{}";

    private final AnonymousUserResolver anonymousUserResolver;
    private final FileUploadRepository fileUploadRepository;
    private final PhoneDrawingRepository phoneDrawingRepository;
    private final ObjectMapper objectMapper;
    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public PhoneDrawingServiceImpl(AnonymousUserResolver anonymousUserResolver,
        FileUploadRepository fileUploadRepository, PhoneDrawingRepository phoneDrawingRepository,
        ObjectMapper objectMapper, MinioPublicUrlResolver minioPublicUrlResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.fileUploadRepository = fileUploadRepository;
        this.phoneDrawingRepository = phoneDrawingRepository;
        this.objectMapper = objectMapper;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    @Override
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

        UUID imageFileId = parseRequiredFileId(request == null ? null : request.imageFileId(),
            INVALID_IMAGE_FILE_ID_MESSAGE);
        UUID thumbnailFileId = parseOptionalFileId(request == null ? null : request.thumbnailFileId(),
            INVALID_THUMBNAIL_FILE_ID_MESSAGE);
        String meta = serializeMeta(request == null ? null : request.meta());
        StructuredEventLogger.apiBusiness("phone_drawing_save_requested", ARTIFACT_KIND_PHONE, userUuid.toString(),
            StructuredEventLogger.metadata("image_file_id", imageFileId, "thumbnail_file_id", thumbnailFileId, "result",
                "requested"));

        FileUpload imageFileUpload = findFileUpload(imageFileId);
        validatePhoneFile(imageFileUpload, userUuid);

        FileUpload thumbnailFileUpload = null;
        if (thumbnailFileId != null) {
            thumbnailFileUpload = findFileUpload(thumbnailFileId);
            validatePhoneFile(thumbnailFileUpload, userUuid);
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

    private UUID parseRequiredFileId(String value, String invalidMessage) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(invalidMessage);
        }

        return parseFileId(value, invalidMessage);
    }

    private UUID parseOptionalFileId(String value, String invalidMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return parseFileId(value, invalidMessage);
    }

    private UUID parseFileId(String value, String invalidMessage) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(invalidMessage);
        }
    }

    private FileUpload findFileUpload(UUID fileId) {
        return fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));
    }

    private void validatePhoneFile(FileUpload fileUpload, UUID userUuid) {
        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.hasPurpose(FileUploadPurpose.PHONE)) {
            throw new BadRequestException(INVALID_PHONE_FILE_MESSAGE);
        }

        if (fileUpload.isDeleted() || !fileUpload.isUploaded()) {
            throw new ConflictException(FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }
    }

    private String serializeMeta(JsonNode meta) {
        if (meta == null || meta.isNull()) {
            return EMPTY_META_JSON;
        }

        if (!meta.isObject()) {
            throw new BadRequestException(INVALID_META_MESSAGE);
        }

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(INVALID_META_MESSAGE);
        }
    }
}
