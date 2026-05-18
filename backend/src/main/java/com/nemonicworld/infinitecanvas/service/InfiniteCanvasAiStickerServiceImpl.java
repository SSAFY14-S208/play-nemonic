package com.nemonicworld.infinitecanvas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.ServiceUnavailableException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.infinitecanvas.config.InfiniteCanvasAiStickerProperties;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasAiStickerCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasAiStickerCreateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerException;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerGmsClient;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerGmsRequest;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerImage;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerStorage;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasStickerPromptTemplateProvider;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasStickerPromptTemplateProvider.CurrentStickerPrompt;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasAiStickerServiceImpl implements InfiniteCanvasAiStickerService {

    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String DISABLED_MESSAGE = "AI 스티커 생성 기능을 사용할 수 없습니다.";
    private static final String INVALID_PROMPT_MESSAGE = "AI 스티커 프롬프트가 올바르지 않습니다.";
    private static final String INVALID_STYLE_MESSAGE = "AI 스티커 스타일이 올바르지 않습니다.";
    private static final String INVALID_SIZE_MESSAGE = "AI 스티커 이미지 크기가 올바르지 않습니다.";
    private static final String IMAGE_URL_CREATE_FAILED_MESSAGE = "AI 스티커 이미지 URL을 생성할 수 없습니다.";
    private static final String GMS_UNAVAILABLE_MESSAGE = "AI 스티커 생성 서비스를 사용할 수 없습니다.";
    private static final String DEFAULT_STYLE = "sticker";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final int DEFAULT_ELEMENT_SIZE = 240;

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasAiStickerProperties properties;
    private final InfiniteCanvasStickerPromptTemplateProvider promptTemplateProvider;
    private final InfiniteCanvasAiStickerGmsClient gmsClient;
    private final InfiniteCanvasAiStickerStorage storage;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ObjectMapper objectMapper;

    public InfiniteCanvasAiStickerServiceImpl(AnonymousUserResolver anonymousUserResolver,
        RoomCodeGenerator roomCodeGenerator, InfiniteCanvasRepository infiniteCanvasRepository,
        InfiniteCanvasAiStickerProperties properties,
        InfiniteCanvasStickerPromptTemplateProvider promptTemplateProvider, InfiniteCanvasAiStickerGmsClient gmsClient,
        InfiniteCanvasAiStickerStorage storage, MinioPublicUrlResolver minioPublicUrlResolver,
        ObjectMapper objectMapper) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.properties = properties;
        this.promptTemplateProvider = promptTemplateProvider;
        this.gmsClient = gmsClient;
        this.storage = storage;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public InfiniteCanvasAiStickerCreateResponse createSticker(String userUuidValue, String roomCode,
        InfiniteCanvasAiStickerCreateRequest request) {
        if (!properties.resolvedEnabled()) {
            throw new ServiceUnavailableException(DISABLED_MESSAGE);
        }
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = normalizeRoomCode(roomCode);
        InfiniteCanvasState state = findActiveState(normalizedRoomCode);
        requireParticipant(state, userUuid);

        String prompt = normalizePrompt(request == null ? null : request.prompt());
        String style = normalizeStyle(request == null ? null : request.style());
        int width = normalizeSize(request == null ? null : request.width());
        int height = normalizeSize(request == null ? null : request.height());
        boolean transparentBackground = request == null || request.transparentBackground() == null
            || request.transparentBackground();
        UUID stickerId = UUID.randomUUID();
        String objectKey = createObjectKey(normalizedRoomCode, stickerId);
        CurrentStickerPrompt currentPrompt = promptTemplateProvider.resolveCurrent();
        long startedAtNanos = System.nanoTime();

        logRequested(userUuid, normalizedRoomCode, stickerId, style, currentPrompt.version());
        try {
            InfiniteCanvasAiStickerImage image = gmsClient.generate(new InfiniteCanvasAiStickerGmsRequest(
                currentPrompt.template(), prompt, style, width, height, transparentBackground));
            String contentType = StringUtils.hasText(image.contentType()) ? image.contentType() : PNG_CONTENT_TYPE;
            storage.upload(objectKey, image.bytes(), contentType);
            String imageUrl = minioPublicUrlResolver.resolve(objectKey);
            if (!StringUtils.hasText(imageUrl)) {
                throw new ServiceUnavailableException(IMAGE_URL_CREATE_FAILED_MESSAGE);
            }
            logCreated(userUuid, normalizedRoomCode, stickerId, style, currentPrompt.version(), image.bytes().length,
                elapsedMillis(startedAtNanos));

            return new InfiniteCanvasAiStickerCreateResponse(stickerId.toString(), imageUrl, objectKey, contentType,
                width, height,
                createElement(stickerId, imageUrl, objectKey, prompt, style, width, height, currentPrompt.version()));
        } catch (InfiniteCanvasAiStickerException | ServiceUnavailableException e) {
            logFailed(userUuid, normalizedRoomCode, stickerId, style, currentPrompt.version(),
                elapsedMillis(startedAtNanos), e);
            throw new ServiceUnavailableException(GMS_UNAVAILABLE_MESSAGE, e);
        }
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
        if (!state.isActive()) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
    }

    private void requireParticipant(InfiniteCanvasState state, String userUuid) {
        if (!state.hasParticipant(userUuid)) {
            throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
        }
    }

    private String normalizePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new BadRequestException(INVALID_PROMPT_MESSAGE);
        }
        String trimmed = prompt.trim();
        if (trimmed.length() > properties.resolvedMaxPromptLength()) {
            throw new BadRequestException(INVALID_PROMPT_MESSAGE);
        }

        return trimmed;
    }

    private String normalizeStyle(String style) {
        String normalized = StringUtils.hasText(style) ? style.trim().toLowerCase(Locale.ROOT) : DEFAULT_STYLE;
        if (!properties.resolvedAllowedStyles().contains(normalized)) {
            throw new BadRequestException(INVALID_STYLE_MESSAGE);
        }

        return normalized;
    }

    private int normalizeSize(Integer size) {
        int normalized = size == null ? properties.resolvedDefaultImageSize() : size;
        if (normalized < 128 || normalized > properties.resolvedMaxImageSize()) {
            throw new BadRequestException(INVALID_SIZE_MESSAGE);
        }

        return normalized;
    }

    private String createObjectKey(String roomCode, UUID stickerId) {
        return "infinite-canvas/ai-stickers/%s/%s.png".formatted(roomCode, stickerId);
    }

    private JsonNode createElement(UUID stickerId, String imageUrl, String objectKey, String prompt, String style,
        int width, int height, String promptVersion) {
        ObjectNode element = objectMapper.createObjectNode();
        element.put("id", "ai-sticker-%s".formatted(stickerId));
        element.put("type", "image");
        element.put("src", imageUrl);
        element.put("objectKey", objectKey);
        element.put("width", DEFAULT_ELEMENT_SIZE);
        element.put("height", Math.max(1, Math.round(DEFAULT_ELEMENT_SIZE * (height / (float) width))));
        element.put("naturalWidth", width);
        element.put("naturalHeight", height);
        ObjectNode metadata = element.putObject("metadata");
        metadata.put("source", "ai_sticker");
        metadata.put("prompt", prompt);
        metadata.put("style", style);
        metadata.put("promptVersion", promptVersion);
        metadata.put("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());

        return element;
    }

    private void logRequested(String userUuid, String roomCode, UUID stickerId, String style, String promptVersion) {
        StructuredEventLogger.apiBusiness("infinite_canvas_ai_sticker_requested", "infinite_canvas", userUuid,
            StructuredEventLogger.metadata("room_id", roomCode, "sticker_id", stickerId, "style", style,
                "prompt_version", promptVersion, "result", "requested"));
    }

    private void logCreated(String userUuid, String roomCode, UUID stickerId, String style, String promptVersion,
        int byteSize, long latencyMs) {
        StructuredEventLogger.apiBusiness("infinite_canvas_ai_sticker_created", "infinite_canvas", userUuid,
            StructuredEventLogger.metadata("room_id", roomCode, "sticker_id", stickerId, "style", style,
                "prompt_version", promptVersion, "byte_size", byteSize, "gms_latency_ms", latencyMs, "result",
                "success"));
    }

    private void logFailed(String userUuid, String roomCode, UUID stickerId, String style, String promptVersion,
        long latencyMs, RuntimeException error) {
        StructuredEventLogger.apiBusinessWarn("infinite_canvas_ai_sticker_failed", "infinite_canvas", userUuid,
            "AI 스티커 생성에 실패했습니다.",
            StructuredEventLogger.metadata("room_id", roomCode, "sticker_id", stickerId, "style", style,
                "prompt_version", promptVersion, "gms_latency_ms", latencyMs, "result", "failed", "reason_code",
                error.getClass().getSimpleName()),
            error);
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
