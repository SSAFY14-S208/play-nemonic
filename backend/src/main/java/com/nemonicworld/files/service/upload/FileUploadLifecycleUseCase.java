package com.nemonicworld.files.service.upload;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.PayloadTooLargeException;
import com.nemonicworld.files.dto.response.FileConfirmResponse;
import com.nemonicworld.files.dto.response.FileDeleteResponse;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadStatus;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.files.service.support.FileStorageSupport;
import com.nemonicworld.files.service.support.FileUploadEventLogger;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import com.nemonicworld.user.service.AnonymousUserResolver;
import io.minio.StatObjectResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FileUploadLifecycleUseCase {

    private static final String INVALID_FILE_ID_MESSAGE = "유효하지 않은 fileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    private static final String FILE_DELETE_STATUS_CONFLICT_MESSAGE = "삭제할 수 없는 파일 업로드 상태입니다.";

    private final MinioStorageProperties properties;
    private final FileUploadRepository fileUploadRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FileStorageSupport fileStorageSupport;
    private final FileUploadEventLogger fileUploadEventLogger;

    public FileUploadLifecycleUseCase(MinioStorageProperties properties, FileUploadRepository fileUploadRepository,
        AnonymousUserResolver anonymousUserResolver, FileStorageSupport fileStorageSupport,
        FileUploadEventLogger fileUploadEventLogger) {
        this.properties = properties;
        this.fileUploadRepository = fileUploadRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fileStorageSupport = fileStorageSupport;
        this.fileUploadEventLogger = fileUploadEventLogger;
    }

    /**
     * MinIO에 실제 객체가 업로드되었는지 확인하고 pending 업로드를 uploaded 상태로 확정합니다.
     */
    @Transactional
    public FileConfirmResponse confirmUpload(String userUuidValue, String fileIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID fileId = parseFileId(fileIdValue);

        anonymousUserResolver.resolve(userUuid);

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));

        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.isPending()) {
            throw new ConflictException(FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }

        StatObjectResponse statObjectResponse = fileStorageSupport.statObject(fileUpload.getObjectKey());

        if (statObjectResponse.size() > properties.maxUploadByteSize()) {
            throw new PayloadTooLargeException(createFileSizeExceededMessage());
        }

        fileUpload.markUploaded(LocalDateTime.now());
        fileUploadEventLogger.logFileEvent("file_upload_confirmed", userUuid, fileUpload,
            StructuredEventLogger.metadata("stat_object_size", statObjectResponse.size()));
        fileUploadEventLogger.logCommunityFileEvent("community_memo_upload_confirmed", userUuid, fileUpload,
            StructuredEventLogger.metadata("stat_object_size", statObjectResponse.size()));

        return new FileConfirmResponse(fileUpload.getId().toString(), FileUploadStatus.UPLOADED.name());
    }

    /**
     * 대기 상태 업로드를 취소하고 MinIO 객체와 DB 메타데이터를 삭제 상태로 정리합니다.
     */
    @Transactional
    public FileDeleteResponse deleteUpload(String userUuidValue, String fileIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID fileId = parseFileId(fileIdValue);

        anonymousUserResolver.resolve(userUuid);

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));

        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.isPending()) {
            throw new ConflictException(FILE_DELETE_STATUS_CONFLICT_MESSAGE);
        }

        fileStorageSupport.removeObject(fileUpload.getObjectKey());

        fileUpload.markDeleted(LocalDateTime.now());
        fileUploadEventLogger.logFileEvent("file_upload_deleted", userUuid, fileUpload,
            StructuredEventLogger.metadata("delete_scope", "pending_upload"));
        fileUploadEventLogger.logCommunityFileEvent("community_memo_upload_deleted", userUuid, fileUpload,
            StructuredEventLogger.metadata("delete_scope", "pending_upload"));

        return new FileDeleteResponse(fileUpload.getId().toString(), FileUploadStatus.DELETED.name());
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

    private String createFileSizeExceededMessage() {
        long maxSizeMb = properties.maxUploadByteSize() / 1024 / 1024;

        return "파일 크기가 제한을 초과했습니다 (최대 %dMB).".formatted(maxSizeMb);
    }
}
