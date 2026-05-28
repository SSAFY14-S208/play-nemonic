package com.nemonicworld.community.service.memo;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.entity.CommunityMemoSourceType;
import com.nemonicworld.community.repository.CommunityMemoRepository;
import com.nemonicworld.community.repository.CommunityMemoSourceGalleryRow;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class CommunityMemoSourceFileSupport {

    private static final String UNSUPPORTED_SOURCE_TYPE_MESSAGE = "지원하지 않는 커뮤니티 메모 sourceType입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String GALLERY_ITEM_NOT_FOUND_MESSAGE = "존재하지 않는 갤러리 항목입니다.";

    private final CommunityMemoRepository communityMemoRepository;
    private final FileUploadRepository fileUploadRepository;

    CommunityMemoSourceFileSupport(CommunityMemoRepository communityMemoRepository,
        FileUploadRepository fileUploadRepository) {
        this.communityMemoRepository = communityMemoRepository;
        this.fileUploadRepository = fileUploadRepository;
    }

    CommunityMemoSourceType validateSourceType(CommunityMemoCreateRequest request) {
        String sourceType = request == null ? null : request.sourceType();
        if (CommunityMemoSourceType.DIRECT.value().equals(sourceType)) {
            if (StringUtils.hasText(request.sourceGalleryId())) {
                throw new BadRequestException(CommunityMemoSupport.INVALID_MEMO_SOURCE_MESSAGE);
            }

            return CommunityMemoSourceType.DIRECT;
        }

        if (CommunityMemoSourceType.GALLERY.value().equals(sourceType)) {
            if (!StringUtils.hasText(request.sourceGalleryId())) {
                throw new BadRequestException(CommunityMemoSupport.INVALID_SOURCE_GALLERY_ID_MESSAGE);
            }

            return CommunityMemoSourceType.GALLERY;
        }

        throw new BadRequestException(UNSUPPORTED_SOURCE_TYPE_MESSAGE);
    }

    UUID resolveSourceArtifactId(CommunityMemoSourceType sourceType, String sourceGalleryIdValue, UUID userUuid) {
        if (sourceType == CommunityMemoSourceType.DIRECT) {
            return null;
        }

        UUID sourceGalleryId = parseFileId(sourceGalleryIdValue,
            CommunityMemoSupport.INVALID_SOURCE_GALLERY_ID_MESSAGE);
        CommunityMemoSourceGalleryRow sourceGallery = communityMemoRepository
            .findActiveSourceGallery(sourceGalleryId, userUuid)
            .orElseThrow(() -> new NotFoundException(GALLERY_ITEM_NOT_FOUND_MESSAGE));

        return sourceGallery.artifactId();
    }

    UUID parseFileId(String fileIdValue, String invalidMessage) {
        if (!StringUtils.hasText(fileIdValue)) {
            throw new BadRequestException(invalidMessage);
        }

        try {
            return UUID.fromString(fileIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(invalidMessage);
        }
    }

    void validateDifferentFiles(UUID originalFileId, UUID thumbnailFileId) {
        if (originalFileId.equals(thumbnailFileId)) {
            throw new BadRequestException(CommunityMemoSupport.DUPLICATED_FILE_MESSAGE);
        }
    }

    void validateCommunityFile(FileUpload fileUpload, UUID userUuid) {
        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.hasPurpose(FileUploadPurpose.COMMUNITY)) {
            throw new BadRequestException(CommunityMemoSupport.INVALID_MEMO_SOURCE_MESSAGE);
        }

        if (fileUpload.isDeleted() || !fileUpload.isUploaded()) {
            throw new ConflictException(CommunityMemoSupport.FILE_UPLOAD_STATUS_CONFLICT_MESSAGE);
        }
    }

    FileUpload findFileUpload(UUID fileId) {
        return fileUploadRepository.findById(fileId)
            .orElseThrow(() -> new NotFoundException(FILE_UPLOAD_NOT_FOUND_MESSAGE));
    }
}
