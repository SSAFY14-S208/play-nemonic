package com.nemonicworld.flipbook.service.submission;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FlipbookFrameFileSupport {

    private static final String INVALID_FILE_ID_MESSAGE = "유효하지 않은 fileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "업로드 완료된 파일만 제출할 수 있습니다.";
    private static final String FILE_UPLOAD_PURPOSE_CONFLICT_MESSAGE = "플립북 프레임 파일만 제출할 수 있습니다.";

    private final FileUploadRepository fileUploadRepository;

    public FlipbookFrameFileSupport(FileUploadRepository fileUploadRepository) {
        this.fileUploadRepository = fileUploadRepository;
    }

    public FileUpload resolveFrameFile(UUID viewerUserUuid, String fileIdValue) {
        UUID fileId = parseFileId(fileIdValue);
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));

        if (!fileUpload.isOwnedBy(viewerUserUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.isUploaded()) {
            throw new ConflictException(FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }

        if (fileUpload.getPurpose() != FileUploadPurpose.FLIPBOOK) {
            throw new ConflictException(FILE_UPLOAD_PURPOSE_CONFLICT_MESSAGE);
        }

        return fileUpload;
    }

    private UUID parseFileId(String fileIdValue) {
        if (!StringUtils.hasText(fileIdValue)) {
            throw new BadRequestException(INVALID_FILE_ID_MESSAGE);
        }

        try {
            return UUID.fromString(fileIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_FILE_ID_MESSAGE);
        }
    }
}
