package com.nemonicworld.infinitecanvas.service.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.files.entity.FileUploadPurpose;
import com.nemonicworld.files.repository.FileUploadRepository;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOutputSaveRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOutputSaveResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasOutputCreateCommand;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasOutputRepository;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasOutputSaveUseCase {

    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String ARTIFACT_KIND_INFINITE_CANVAS = "infinite_canvas";
    private static final String INVALID_IMAGE_FILE_ID_MESSAGE = "유효하지 않은 imageFileId 형식입니다.";
    private static final String INVALID_THUMBNAIL_FILE_ID_MESSAGE = "유효하지 않은 thumbnailFileId 형식입니다.";
    private static final String FILE_UPLOAD_NOT_FOUND_MESSAGE = "파일 업로드 정보를 찾을 수 없습니다.";
    private static final String FILE_ACCESS_DENIED_MESSAGE = "파일에 접근할 권한이 없습니다.";
    private static final String INVALID_INFINITE_CANVAS_FILE_MESSAGE = "무한 캔버스 출력 파일만 저장할 수 있습니다.";
    private static final String FILE_UPLOAD_STATUS_CONFLICT_MESSAGE = "확인할 수 없는 파일 업로드 상태입니다.";
    private static final String INVALID_META_MESSAGE = "메타데이터 형식이 올바르지 않습니다.";
    private static final String EMPTY_META_JSON = "{}";

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasOutputRepository infiniteCanvasOutputRepository;
    private final FileUploadRepository fileUploadRepository;
    private final ObjectMapper objectMapper;
    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public InfiniteCanvasOutputSaveUseCase(AnonymousUserResolver anonymousUserResolver,
        RoomCodeGenerator roomCodeGenerator, InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasOutputRepository infiniteCanvasOutputRepository, FileUploadRepository fileUploadRepository,
        ObjectMapper objectMapper, MinioPublicUrlResolver minioPublicUrlResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.infiniteCanvasOutputRepository = infiniteCanvasOutputRepository;
        this.fileUploadRepository = fileUploadRepository;
        this.objectMapper = objectMapper;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    @Transactional
    public InfiniteCanvasOutputSaveResponse saveOutput(String userUuidValue, String roomCode,
        InfiniteCanvasOutputSaveRequest request) {
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        UUID userUuid = user.getId();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        InfiniteCanvasState state = findActiveState(normalizedRoomCode);
        requireParticipant(state, userUuid.toString());

        UUID imageFileId = parseRequiredFileId(request == null ? null : request.imageFileId(),
            INVALID_IMAGE_FILE_ID_MESSAGE);
        UUID thumbnailFileId = parseOptionalFileId(request == null ? null : request.thumbnailFileId(),
            INVALID_THUMBNAIL_FILE_ID_MESSAGE);
        String meta = serializeMeta(request == null ? null : request.meta());

        FileUpload imageFileUpload = findFileUpload(imageFileId);
        validateInfiniteCanvasFile(imageFileUpload, userUuid);

        FileUpload thumbnailFileUpload = null;
        if (thumbnailFileId != null) {
            thumbnailFileUpload = findFileUpload(thumbnailFileId);
            validateInfiniteCanvasFile(thumbnailFileUpload, userUuid);
        }

        String imageObjectKey = imageFileUpload.getObjectKey();
        String thumbnailObjectKey = thumbnailFileUpload == null ? imageObjectKey : thumbnailFileUpload.getObjectKey();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID artifactId = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();

        infiniteCanvasOutputRepository.save(new InfiniteCanvasOutputCreateCommand(galleryId, artifactId, userUuid,
            ARTIFACT_KIND_INFINITE_CANVAS, normalizedRoomCode, imageObjectKey, thumbnailObjectKey, meta, now, now));

        return new InfiniteCanvasOutputSaveResponse(galleryId.toString(), artifactId.toString(),
            ARTIFACT_KIND_INFINITE_CANVAS, normalizedRoomCode, minioPublicUrlResolver.resolve(thumbnailObjectKey),
            minioPublicUrlResolver.resolve(imageObjectKey), now);
    }

    private String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode) || !roomCodeGenerator.isValid(roomCode.trim())) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }

        return roomCode.trim();
    }

    private InfiniteCanvasState findActiveState(String roomCode) {
        InfiniteCanvasState state = infiniteCanvasRepository.findByRoomCode(roomCode)
            .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
        if (state.status() == InfiniteCanvasStatus.CLOSED) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
    }

    private void requireParticipant(InfiniteCanvasState state, String userUuid) {
        if (!state.hasParticipant(userUuid)) {
            throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
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

    private void validateInfiniteCanvasFile(FileUpload fileUpload, UUID userUuid) {
        if (!fileUpload.isOwnedBy(userUuid)) {
            throw new ForbiddenException(FILE_ACCESS_DENIED_MESSAGE);
        }

        if (!fileUpload.hasPurpose(FileUploadPurpose.INFINITE_CANVAS)) {
            throw new BadRequestException(INVALID_INFINITE_CANVAS_FILE_MESSAGE);
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
