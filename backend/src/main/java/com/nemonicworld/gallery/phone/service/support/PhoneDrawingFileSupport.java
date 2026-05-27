package com.nemonicworld.gallery.phone.service.support;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PhoneDrawingFileSupport {

    private static final String INVALID_IMAGE_FILE_ID_MESSAGE = "유효하지 않은 imageFileId 형식입니다.";
    private static final String INVALID_THUMBNAIL_FILE_ID_MESSAGE = "유효하지 않은 thumbnailFileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String INVALID_PHONE_FILE_MESSAGE = "휴대폰 그림 파일만 저장할 수 있습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";

    private final FileUploadRepository fileUploadRepository;

    public PhoneDrawingFileSupport(FileUploadRepository fileUploadRepository) {
        this.fileUploadRepository = fileUploadRepository;
    }

    public UUID parseRequiredImageFileId(String value) {
        return parseRequiredFileId(value, INVALID_IMAGE_FILE_ID_MESSAGE);
    }

    public UUID parseOptionalThumbnailFileId(String value) {
        return parseOptionalFileId(value, INVALID_THUMBNAIL_FILE_ID_MESSAGE);
    }

    public FileUpload findFileUpload(UUID fileId) {
        return fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));
    }

    public void validatePhoneFile(FileUpload fileUpload, UUID userUuid) {
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
}
