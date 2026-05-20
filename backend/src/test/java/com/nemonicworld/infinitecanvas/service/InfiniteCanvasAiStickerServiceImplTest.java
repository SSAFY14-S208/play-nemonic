package com.nemonicworld.infinitecanvas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.infinitecanvas.config.InfiniteCanvasAiStickerProperties;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasAiStickerCreateRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasAiStickerCreateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.repository.InfiniteCanvasRepository;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerGmsClient;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerGmsRequest;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerImage;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasAiStickerStorage;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasStickerPromptTemplateProvider;
import com.nemonicworld.infinitecanvas.service.ai.InfiniteCanvasStickerPromptTemplateProvider.CurrentStickerPrompt;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfiniteCanvasAiStickerServiceImplTest {

    private static final String ROOM_CODE = "AC3K9Q";
    private static final UUID USER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private InfiniteCanvasRepository infiniteCanvasRepository;

    @Mock
    private InfiniteCanvasStickerPromptTemplateProvider promptTemplateProvider;

    @Mock
    private InfiniteCanvasAiStickerGmsClient gmsClient;

    @Mock
    private InfiniteCanvasAiStickerStorage storage;

    @Mock
    private MinioPublicUrlResolver minioPublicUrlResolver;

    private InfiniteCanvasAiStickerServiceImpl service;

    @BeforeEach
    void setUp() {
        InfiniteCanvasAiStickerProperties properties = new InfiniteCanvasAiStickerProperties(true, 200, 512, 1024,
            Set.of("sticker", "cartoon"), new InfiniteCanvasAiStickerProperties.Gms("test-key", "gpt-image-1",
                "https://example.com/images", 1000L, 5000L));
        service = new InfiniteCanvasAiStickerServiceImpl(anonymousUserResolver, roomCodeGenerator,
            infiniteCanvasRepository, properties, promptTemplateProvider, gmsClient, storage, minioPublicUrlResolver,
            new ObjectMapper());
    }

    @Test
    void createStickerGeneratesImageStoresItAndReturnsCanvasElement() {
        AppUser user = user();
        InfiniteCanvasState state = activeState(USER_UUID);
        given(anonymousUserResolver.resolve(USER_UUID.toString())).willReturn(user);
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(infiniteCanvasRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(state));
        given(promptTemplateProvider.resolveCurrent())
            .willReturn(new CurrentStickerPrompt("Make sticker", "42", "database", null));
        given(gmsClient.generate(org.mockito.ArgumentMatchers.any()))
            .willReturn(new InfiniteCanvasAiStickerImage(new byte[]{1, 2, 3}, "image/png"));
        given(minioPublicUrlResolver
            .resolve(org.mockito.ArgumentMatchers.startsWith("infinite-canvas/ai-stickers/AC3K9Q/")))
            .willReturn("http://localhost:9000/nemonic-local/sticker.png");

        InfiniteCanvasAiStickerCreateResponse response = service.createSticker(USER_UUID.toString(), ROOM_CODE,
            new InfiniteCanvasAiStickerCreateRequest("바이올린을 켜는 토끼", "sticker", 512, 512, true));

        assertThat(response.imageUrl()).isEqualTo("http://localhost:9000/nemonic-local/sticker.png");
        assertThat(response.width()).isEqualTo(1024);
        assertThat(response.height()).isEqualTo(1024);
        assertThat(response.objectKey()).startsWith("infinite-canvas/ai-stickers/AC3K9Q/");
        assertThat(response.element().path("type").asText()).isEqualTo("image");
        assertThat(response.element().path("src").asText()).isEqualTo(response.imageUrl());
        assertThat(response.element().path("naturalWidth").asInt()).isEqualTo(1024);
        assertThat(response.element().path("naturalHeight").asInt()).isEqualTo(1024);
        assertThat(response.element().path("metadata").path("source").asText()).isEqualTo("ai_sticker");
        assertThat(response.element().path("metadata").path("promptVersion").asText()).isEqualTo("42");

        ArgumentCaptor<InfiniteCanvasAiStickerGmsRequest> requestCaptor = ArgumentCaptor
            .forClass(InfiniteCanvasAiStickerGmsRequest.class);
        verify(gmsClient).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().systemPrompt()).isEqualTo("Make sticker");
        assertThat(requestCaptor.getValue().userPrompt()).isEqualTo("바이올린을 켜는 토끼");
        assertThat(requestCaptor.getValue().width()).isEqualTo(1024);
        assertThat(requestCaptor.getValue().height()).isEqualTo(1024);
        verify(storage).upload(eq(response.objectKey()), aryEq(new byte[]{1, 2, 3}), eq("image/png"));
    }

    @Test
    void createStickerRejectsNonParticipantBeforeCallingGms() {
        UUID otherUserUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        given(anonymousUserResolver.resolve(otherUserUuid.toString())).willReturn(user(otherUserUuid));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(infiniteCanvasRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(activeState(USER_UUID)));

        assertThatThrownBy(() -> service.createSticker(otherUserUuid.toString(), ROOM_CODE,
            new InfiniteCanvasAiStickerCreateRequest("토끼", "sticker", null, null, null)))
            .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(gmsClient, storage);
    }

    @Test
    void createStickerRejectsUnsupportedStyle() {
        given(anonymousUserResolver.resolve(USER_UUID.toString())).willReturn(user());
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(infiniteCanvasRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(activeState(USER_UUID)));

        assertThatThrownBy(() -> service.createSticker(USER_UUID.toString(), ROOM_CODE,
            new InfiniteCanvasAiStickerCreateRequest("토끼", "oil", null, null, null)))
            .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(gmsClient, storage);
    }

    private AppUser user() {
        return user(USER_UUID);
    }

    private AppUser user(UUID userUuid) {
        AppUser user = AppUser.createAnonymous(userUuid, "MangoApp/1.0", LocalDateTime.now());
        user.updateNickname("망고", LocalDateTime.now());
        return user;
    }

    private InfiniteCanvasState activeState(UUID userUuid) {
        LocalDateTime now = LocalDateTime.now();
        InfiniteCanvasParticipant participant = new InfiniteCanvasParticipant(userUuid.toString(), "망고", "#72DDF7",
            null, true, true, now, now, now);

        return new InfiniteCanvasState(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, userUuid.toString(),
            List.of(participant), List.of(), List.of(), Map.of(), Map.of(), null, 6, 0L, now, now, null);
    }
}
