package com.nemonicworld.files.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.PayloadTooLargeException;
import com.nemonicworld.files.config.MinioStorageProperties;
import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FileConfirmResponse;
import com.nemonicworld.files.dto.response.FileDeleteResponse;
import com.nemonicworld.files.dto.response.FilePresignResponse;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.entity.FileUploadStatus;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.user.repository.UserRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 파일 업로드 URL 발급 흐름을 처리하는 서비스 구현체입니다.
 *
 * <p>
 * 실제 파일 바이너리는 백엔드를 거치지 않고 클라이언트가 MinIO에 직접 PUT 업로드합니다. 이 서비스는 사용자와 요청값을 검증하고,
 * 이후 confirm/delete API에서 추적할 수 있도록 file_upload pending 메타데이터를 먼저 저장합니다.
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";
    private static final String INVALID_FILE_NAME_MESSAGE = "파일명이 올바르지 않습니다.";
    private static final String INVALID_BYTE_SIZE_MESSAGE = "파일 크기가 올바르지 않습니다.";
    private static final String UNSUPPORTED_FILE_TYPE_MESSAGE = "지원하지 않는 파일 형식입니다.";
    private static final String UNSUPPORTED_PURPOSE_MESSAGE = "지원하지 않는 purpose입니다.";
    private static final String INVALID_FILE_ID_MESSAGE = "유효하지 않은 fileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    private static final String FILE_STORAGE_ERROR_MESSAGE = "파일 저장소 처리 중 오류가 발생했습니다.";
    private static final String MINIO_NO_SUCH_KEY_CODE = "NoSuchKey";
    private static final String MINIO_NO_SUCH_OBJECT_CODE = "NoSuchObject";
    private static final String FILE_DELETE_STATUS_CONFLICT_MESSAGE = "삭제할 수 없는 파일 업로드 상태입니다.";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif",
        "image/webp");

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;
    private final FileUploadRepository fileUploadRepository;
    private final UserRepository userRepository;

    /**
     * 업로드 가능한 사용자와 파일 요청인지 검증한 뒤, DB에 pending 업로드 기록을 남기고 임시 PUT URL을 반환합니다.
     */
    @Override
    @Transactional
    public FilePresignResponse createPresignedUrl(String userUuidValue, FilePresignRequest request) {
        UUID userUuid = parseUserUuid(userUuidValue);

        if (!userRepository.existsById(userUuid)) {
            throw new NotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        String safeFileName = validateAndGetSafeFileName(request.fileName());
        validateContentType(request.contentType());
        long byteSize = validateAndGetByteSize(request.byteSize());
        FileUploadPurpose purpose = parsePurpose(request.purpose());

        UUID fileId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        int expiresIn = properties.presignExpirationSeconds();
        LocalDateTime expiresAt = now.plusSeconds(expiresIn);
        String objectKey = createObjectKey(purpose, fileId, safeFileName);
        String presignedUrl = createPutPresignedUrl(objectKey, request.contentType(), expiresIn);

        FileUpload fileUpload = FileUpload.createPending(fileId, userUuid, purpose, safeFileName, request.contentType(),
            byteSize, objectKey, expiresAt, now);
        fileUploadRepository.save(fileUpload);

        return new FilePresignResponse(fileId.toString(), presignedUrl, expiresIn);
    }

    /**
     * MinIO에 실제 객체가 업로드되었는지 확인하고 pending 업로드를 uploaded 상태로 확정합니다.
     */
    @Override
    @Transactional
    public FileConfirmResponse confirmUpload(String userUuidValue, String fileIdValue) {
        UUID userUuid = parseUserUuid(userUuidValue);
        UUID fileId = parseFileId(fileIdValue);

        if (!userRepository.existsById(userUuid)) {
            throw new NotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));

        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.isPending()) {
            throw new ConflictException(FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }

        StatObjectResponse statObjectResponse = statObject(fileUpload.getObjectKey());

        if (statObjectResponse.size() > properties.maxUploadByteSize()) {
            throw new PayloadTooLargeException(createFileSizeExceededMessage());
        }

        fileUpload.markUploaded(LocalDateTime.now());

        return new FileConfirmResponse(fileUpload.getId().toString(), FileUploadStatus.UPLOADED.name());
    }

    /**
     * pending 업로드를 취소하고 MinIO object와 DB 메타데이터를 삭제 상태로 정리합니다.
     */
    @Override
    @Transactional
    public FileDeleteResponse deleteUpload(String userUuidValue, String fileIdValue) {
        UUID userUuid = parseUserUuid(userUuidValue);
        UUID fileId = parseFileId(fileIdValue);

        if (!userRepository.existsById(userUuid)) {
            throw new NotFoundException(USER_NOT_FOUND_MESSAGE);
        }

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));

        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.isPending()) {
            throw new ConflictException(FILE_DELETE_STATUS_CONFLICT_MESSAGE);
        }

        removeObject(fileUpload.getObjectKey());

        fileUpload.markDeleted(LocalDateTime.now());

        return new FileDeleteResponse(fileUpload.getId().toString(), FileUploadStatus.DELETED.name());
    }

    /**
     * 헤더로 전달된 익명 사용자 UUID가 비어 있거나 UUID 형식이 아니면 400으로 변환합니다.
     */
    private UUID parseUserUuid(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
    }

    private UUID parseFileId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(INVALID_FILE_ID_MESSAGE);
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_FILE_ID_MESSAGE);
        }
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
     * object key에 그대로 포함해도 되는 파일명인지 확인합니다.
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
     * 운영자가 추적하기 쉽고 충돌이 나지 않도록 날짜와 fileId를 포함한 MinIO object key를 만듭니다.
     */
    private String createObjectKey(FileUploadPurpose purpose, UUID fileId, String safeFileName) {
        LocalDate today = LocalDate.now();

        return "uploads/%s/%04d/%02d/%02d/%s/%s".formatted(purpose.name().toLowerCase(Locale.ROOT), today.getYear(),
            today.getMonthValue(), today.getDayOfMonth(), fileId, safeFileName);
    }

    /**
     * MinIO에 직접 PUT 업로드할 수 있는 만료 시간 제한 URL을 생성합니다.
     */
    private String createPutPresignedUrl(String objectKey, String contentType, int expiresIn) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(properties.bucket()).object(objectKey)
                    .expiry(expiresIn).extraHeaders(Map.of("Content-Type", contentType)).build());
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    private StatObjectResponse statObject(String objectKey) {
        try {
            return minioClient
                .statObject(StatObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (ErrorResponseException e) {
            if (isObjectNotFound(e)) {
                throw new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE);
            }

            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    private void removeObject(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (ErrorResponseException e) {
            if (isObjectNotFound(e)) {
                return;
            }

            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        } catch (Exception e) {
            throw new FileStorageException(FILE_STORAGE_ERROR_MESSAGE, e);
        }
    }

    private boolean isObjectNotFound(ErrorResponseException e) {
        String code = e.errorResponse().code();

        return MINIO_NO_SUCH_KEY_CODE.equals(code) || MINIO_NO_SUCH_OBJECT_CODE.equals(code);
    }

}
