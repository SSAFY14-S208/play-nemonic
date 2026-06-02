package com.nemonicworld.files.service.upload;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.PayloadTooLargeException;
import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FilePresignResponse;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.files.service.support.FileStorageSupport;
import com.nemonicworld.files.service.support.FileUploadEventLogger;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FileUploadPresignUseCase {

    private static final String INVALID_FILE_NAME_MESSAGE = "파일명이 올바르지 않습니다.";
    private static final String INVALID_BYTE_SIZE_MESSAGE = "파일 크기가 올바르지 않습니다.";
    private static final String UNSUPPORTED_FILE_TYPE_MESSAGE = "지원하지 않는 파일 형식입니다.";
    private static final String UNSUPPORTED_PURPOSE_MESSAGE = "지원하지 않는 purpose입니다.";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif",
        "image/webp");

    private final MinioStorageProperties properties;
    private final FileUploadRepository fileUploadRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FileStorageSupport fileStorageSupport;
    private final FileUploadEventLogger fileUploadEventLogger;

    public FileUploadPresignUseCase(MinioStorageProperties properties, FileUploadRepository fileUploadRepository,
        AnonymousUserResolver anonymousUserResolver, FileStorageSupport fileStorageSupport,
        FileUploadEventLogger fileUploadEventLogger) {
        this.properties = properties;
        this.fileUploadRepository = fileUploadRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fileStorageSupport = fileStorageSupport;
        this.fileUploadEventLogger = fileUploadEventLogger;
    }

    /**
     * 업로드 가능한 사용자와 파일 요청인지 검증한 뒤, DB에 pending 업로드 기록을 남기고 임시 PUT URL을 반환합니다.
     */
    @Transactional
    public FilePresignResponse createPresignedUrl(String userUuidValue, FilePresignRequest request) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        anonymousUserResolver.resolve(userUuid);

        String safeFileName = validateAndGetSafeFileName(request.fileName());
        validateContentType(request.contentType());
        long byteSize = validateAndGetByteSize(request.byteSize());
        FileUploadPurpose purpose = parsePurpose(request.purpose());

        UUID fileId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        int expiresIn = properties.presignExpirationSeconds();
        LocalDateTime expiresAt = now.plusSeconds(expiresIn);
        String objectKey = createObjectKey(purpose, fileId, safeFileName);
        String presignedUrl = fileStorageSupport.createPutPresignedUrl(objectKey, request.contentType(), expiresIn);

        FileUpload fileUpload = FileUpload.createPending(fileId, userUuid, purpose, safeFileName, request.contentType(),
            byteSize, objectKey, expiresAt, now);
        fileUploadRepository.save(fileUpload);
        fileUploadEventLogger.logFileEvent("file_presign_requested", userUuid, fileUpload,
            StructuredEventLogger.metadata("expires_at", expiresAt, "expires_in", expiresIn));
        fileUploadEventLogger.logCommunityFileEvent("community_memo_presign_requested", userUuid, fileUpload,
            StructuredEventLogger.metadata("expires_at", expiresAt, "expires_in", expiresIn));

        return new FilePresignResponse(fileId.toString(), presignedUrl, expiresIn);
    }

    /**
     * API에서 받은 purpose 문자열을 Java enum으로 변환합니다.
     */
    private FileUploadPurpose parsePurpose(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(UNSUPPORTED_PURPOSE_MESSAGE);
        }

        try {
            return FileUploadPurpose.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(UNSUPPORTED_PURPOSE_MESSAGE);
        }
    }

    /**
     * 객체 키에 그대로 포함해도 되는 파일명인지 확인합니다.
     */
    private String validateAndGetSafeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BadRequestException(INVALID_FILE_NAME_MESSAGE);
        }

        String trimmedFileName = fileName.trim();
        if (trimmedFileName.contains("/") || trimmedFileName.contains("\\") || trimmedFileName.contains("..")) {
            throw new BadRequestException(INVALID_FILE_NAME_MESSAGE);
        }

        int dotIndex = trimmedFileName.lastIndexOf(".");
        if (dotIndex <= 0 || dotIndex == trimmedFileName.length() - 1) {
            throw new BadRequestException(INVALID_FILE_NAME_MESSAGE);
        }

        return trimmedFileName;
    }

    /**
     * 현재 presign API는 이미지 업로드만 허용합니다.
     */
    private void validateContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException(UNSUPPORTED_FILE_TYPE_MESSAGE);
        }
    }

    /**
     * presign 발급 전 클라이언트가 신고한 파일 크기를 1차 검증합니다. confirm API에서는 MinIO의 실제 object
     * size를 다시 검증해야 합니다.
     */
    private long validateAndGetByteSize(Long byteSize) {
        if (byteSize == null || byteSize <= 0) {
            throw new BadRequestException(INVALID_BYTE_SIZE_MESSAGE);
        }

        if (byteSize > properties.maxUploadByteSize()) {
            throw new PayloadTooLargeException(createFileSizeExceededMessage());
        }

        return byteSize;
    }

    private String createFileSizeExceededMessage() {
        long maxSizeMb = properties.maxUploadByteSize() / 1024 / 1024;

        return "파일 크기가 제한을 초과했습니다 (최대 %dMB).".formatted(maxSizeMb);
    }

    /**
     * 운영자가 추적하기 쉽고 충돌이 나지 않도록 날짜와 fileId를 포함한 MinIO 객체 키를 만듭니다.
     */
    private String createObjectKey(FileUploadPurpose purpose, UUID fileId, String safeFileName) {
        if (purpose == FileUploadPurpose.PHONE) {
            return "phone/results/%s/%s".formatted(fileId, safeFileName);
        }

        LocalDate today = LocalDate.now();

        return "uploads/%s/%04d/%02d/%02d/%s/%s".formatted(purpose.name().toLowerCase(Locale.ROOT), today.getYear(),
            today.getMonthValue(), today.getDayOfMonth(), fileId, safeFileName);
    }
}
