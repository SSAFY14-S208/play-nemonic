package com.nemonicworld.files.service.view;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.files.dto.response.FileViewUrlResponse;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.files.service.support.FileStorageSupport;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FileViewUrlUseCase {

    private static final String INVALID_FILE_ID_MESSAGE = "유효하지 않은 fileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String FILE_VIEW_STATUS_CONFLICT_MESSAGE = "조회할 수 없는 파일 업로드 상태입니다.";

    private final MinioStorageProperties properties;
    private final FileUploadRepository fileUploadRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final FileStorageSupport fileStorageSupport;

    public FileViewUrlUseCase(MinioStorageProperties properties, FileUploadRepository fileUploadRepository,
        AnonymousUserResolver anonymousUserResolver, FileStorageSupport fileStorageSupport) {
        this.properties = properties;
        this.fileUploadRepository = fileUploadRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.fileStorageSupport = fileStorageSupport;
    }

    /**
     * 업로드 완료된 비공개 파일을 브라우저에서 잠깐 조회할 수 있는 GET URL을 반환합니다.
     */
    @Transactional(readOnly = true)
    public FileViewUrlResponse createViewUrl(String userUuidValue, String fileIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID fileId = parseFileId(fileIdValue);

        anonymousUserResolver.resolve(userUuid);

        FileUpload fileUpload = fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));

        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.isUploaded()) {
            throw new ConflictException(FILE_VIEW_STATUS_CONFLICT_MESSAGE);
        }

        fileStorageSupport.statObject(fileUpload.getObjectKey());

        int expiresIn = properties.viewUrlExpirationSeconds();
        String viewUrl = fileStorageSupport.createGetPresignedUrl(fileUpload.getObjectKey(), expiresIn);

        return new FileViewUrlResponse(fileUpload.getId().toString(), viewUrl, expiresIn);
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
}
