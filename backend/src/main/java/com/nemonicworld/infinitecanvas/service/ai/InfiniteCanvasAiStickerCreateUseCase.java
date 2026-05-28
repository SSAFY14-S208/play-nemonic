package com.nemonicworld.infinitecanvas.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.exception.ServiceUnavailableException;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.infinitecanvas.config.InfiniteCanvasAiStickerProperties;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasAiStickerCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasAiStickerCreateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasStickerPromptTemplateProvider.CurrentStickerPrompt;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasAiStickerCreateUseCase {

    private static final String CANVAS_NOT_FOUND_MESSAGE = "활성 무한 캔버스를 찾을 수 없습니다.";
    private static final String DISABLED_MESSAGE = "AI 스티커 생성 기능을 사용할 수 없습니다.";
    private static final String IMAGE_URL_CREATE_FAILED_MESSAGE = "AI 스티커 이미지 URL을 생성할 수 없습니다.";
    private static final String GMS_UNAVAILABLE_MESSAGE = "AI 스티커 생성 서비스를 사용할 수 없습니다.";
    private static final String PNG_CONTENT_TYPE = "image/png";

    private final AnonymousUserResolver anonymousUserResolver;
    private final InfiniteCanvasRepository infiniteCanvasRepository;
    private final InfiniteCanvasAiStickerProperties properties;
    private final InfiniteCanvasStickerPromptTemplateProvider promptTemplateProvider;
    private final InfiniteCanvasAiStickerGmsClient gmsClient;
    private final InfiniteCanvasAiStickerStorage storage;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final InfiniteCanvasAiStickerRequestSupport requestSupport;
    private final InfiniteCanvasAiStickerElementFactory elementFactory;
    private final InfiniteCanvasAiStickerEventLogger eventLogger;

    public InfiniteCanvasAiStickerCreateUseCase(AnonymousUserResolver anonymousUserResolver,
        InfiniteCanvasRepository infiniteCanvasRepository, InfiniteCanvasAiStickerProperties properties,
        InfiniteCanvasStickerPromptTemplateProvider promptTemplateProvider, InfiniteCanvasAiStickerGmsClient gmsClient,
        InfiniteCanvasAiStickerStorage storage, MinioPublicUrlResolver minioPublicUrlResolver,
        InfiniteCanvasAiStickerRequestSupport requestSupport, InfiniteCanvasAiStickerElementFactory elementFactory,
        InfiniteCanvasAiStickerEventLogger eventLogger) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.infiniteCanvasRepository = infiniteCanvasRepository;
        this.properties = properties;
        this.promptTemplateProvider = promptTemplateProvider;
        this.gmsClient = gmsClient;
        this.storage = storage;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.requestSupport = requestSupport;
        this.elementFactory = elementFactory;
        this.eventLogger = eventLogger;
    }

    @Transactional(readOnly = true)
    public InfiniteCanvasAiStickerCreateResponse createSticker(String userUuidValue, String roomCode,
        InfiniteCanvasAiStickerCreateRequest request) {
        if (!properties.resolvedEnabled()) {
            throw new ServiceUnavailableException(DISABLED_MESSAGE);
        }
        AppUser user = anonymousUserResolver.resolve(userUuidValue);
        String userUuid = user.getId().toString();
        String normalizedRoomCode = requestSupport.normalizeRoomCode(roomCode);
        InfiniteCanvasState state = findActiveState(normalizedRoomCode);
        requestSupport.requireParticipant(state, userUuid);

        String prompt = requestSupport.normalizePrompt(request == null ? null : request.prompt());
        String style = requestSupport.normalizeStyle(request == null ? null : request.style());
        int width = requestSupport.normalizeSize(request == null ? null : request.width());
        int height = requestSupport.normalizeSize(request == null ? null : request.height());
        int generationWidth = requestSupport.normalizeGenerationSize(width);
        int generationHeight = requestSupport.normalizeGenerationSize(height);
        boolean transparentBackground = request == null || request.transparentBackground() == null
            || request.transparentBackground();
        UUID stickerId = UUID.randomUUID();
        String objectKey = createObjectKey(normalizedRoomCode, stickerId);
        CurrentStickerPrompt currentPrompt = promptTemplateProvider.resolveCurrent();
        long startedAtNanos = System.nanoTime();

        eventLogger.logRequested(userUuid, normalizedRoomCode, stickerId, style, currentPrompt.version());
        try {
            InfiniteCanvasAiStickerImage image = gmsClient.generate(new InfiniteCanvasAiStickerGmsRequest(
                currentPrompt.template(), prompt, style, generationWidth, generationHeight, transparentBackground));
            String contentType = StringUtils.hasText(image.contentType()) ? image.contentType() : PNG_CONTENT_TYPE;
            storage.upload(objectKey, image.bytes(), contentType);
            String imageUrl = minioPublicUrlResolver.resolve(objectKey);
            if (!StringUtils.hasText(imageUrl)) {
                throw new ServiceUnavailableException(IMAGE_URL_CREATE_FAILED_MESSAGE);
            }
            eventLogger.logCreated(userUuid, normalizedRoomCode, stickerId, style, currentPrompt.version(),
                image.bytes().length, elapsedMillis(startedAtNanos));

            JsonNode element = elementFactory.createElement(stickerId, imageUrl, objectKey, prompt, style,
                generationWidth, generationHeight, currentPrompt.version());
            return new InfiniteCanvasAiStickerCreateResponse(stickerId.toString(), imageUrl, objectKey, contentType,
                generationWidth, generationHeight, element);
        } catch (InfiniteCanvasAiStickerException | ServiceUnavailableException e) {
            eventLogger.logFailed(userUuid, normalizedRoomCode, stickerId, style, currentPrompt.version(),
                elapsedMillis(startedAtNanos), e);
            throw new ServiceUnavailableException(GMS_UNAVAILABLE_MESSAGE, e);
        }
    }

    private InfiniteCanvasState findActiveState(String roomCode) {
        InfiniteCanvasState state = infiniteCanvasRepository.findByRoomCode(roomCode)
            .orElseThrow(() -> new NotFoundException(CANVAS_NOT_FOUND_MESSAGE));
        if (!state.isActive()) {
            throw new NotFoundException(CANVAS_NOT_FOUND_MESSAGE);
        }

        return state;
    }

    private String createObjectKey(String roomCode, UUID stickerId) {
        return "infinite-canvas/ai-stickers/%s/%s.png".formatted(roomCode, stickerId);
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
