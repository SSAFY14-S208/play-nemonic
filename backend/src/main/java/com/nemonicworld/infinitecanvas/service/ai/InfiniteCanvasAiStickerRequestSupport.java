package com.nemonicworld.infinitecanvas.service.ai;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.config.InfiniteCanvasAiStickerProperties;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InfiniteCanvasAiStickerRequestSupport {

    private static final String NOT_PARTICIPANT_MESSAGE = "무한 캔버스 참여자가 아닙니다.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String INVALID_PROMPT_MESSAGE = "AI 스티커 프롬프트가 올바르지 않습니다.";
    private static final String INVALID_STYLE_MESSAGE = "AI 스티커 스타일이 올바르지 않습니다.";
    private static final String INVALID_SIZE_MESSAGE = "AI 스티커 이미지 크기가 올바르지 않습니다.";
    private static final String DEFAULT_STYLE = "sticker";
    private static final int GPT_IMAGE_MIN_SIZE = 1024;

    private final RoomCodeGenerator roomCodeGenerator;
    private final InfiniteCanvasAiStickerProperties properties;

    public InfiniteCanvasAiStickerRequestSupport(RoomCodeGenerator roomCodeGenerator,
        InfiniteCanvasAiStickerProperties properties) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.properties = properties;
    }

    public String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode) || !roomCodeGenerator.isValid(roomCode.trim())) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }

        return roomCode.trim();
    }

    public void requireParticipant(InfiniteCanvasState state, String userUuid) {
        if (!state.hasParticipant(userUuid)) {
            throw new NotFoundException(NOT_PARTICIPANT_MESSAGE);
        }
    }

    public String normalizePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new BadRequestException(INVALID_PROMPT_MESSAGE);
        }
        String trimmed = prompt.trim();
        if (trimmed.length() > properties.resolvedMaxPromptLength()) {
            throw new BadRequestException(INVALID_PROMPT_MESSAGE);
        }

        return trimmed;
    }

    public String normalizeStyle(String style) {
        String normalized = StringUtils.hasText(style) ? style.trim().toLowerCase(Locale.ROOT) : DEFAULT_STYLE;
        if (!properties.resolvedAllowedStyles().contains(normalized)) {
            throw new BadRequestException(INVALID_STYLE_MESSAGE);
        }

        return normalized;
    }

    public int normalizeSize(Integer size) {
        int normalized = size == null ? properties.resolvedDefaultImageSize() : size;
        if (normalized < 128 || normalized > properties.resolvedMaxImageSize()) {
            throw new BadRequestException(INVALID_SIZE_MESSAGE);
        }

        return normalized;
    }

    public int normalizeGenerationSize(int size) {
        if (isGptImageModel()) {
            return Math.max(size, GPT_IMAGE_MIN_SIZE);
        }

        return size;
    }

    private boolean isGptImageModel() {
        return properties.resolvedGms().resolvedModel().toLowerCase(Locale.ROOT).startsWith("gpt-image");
    }
}
