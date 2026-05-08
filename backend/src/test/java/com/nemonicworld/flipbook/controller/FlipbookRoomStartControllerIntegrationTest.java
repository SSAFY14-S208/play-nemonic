package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomParticipantResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import com.nemonicworld.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * 플립북 게임 시작 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookRoomStartControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlipbookRoomService flipbookRoomService;

    @MockitoBean
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    /**
     * POST 요청을 게임 시작 유스케이스로 위임하고 시작 이벤트를 발행합니다.
     */
    @Test
    void startFlipbookRoomReturnsPlayingRoomStateAndPublishesEvent() throws Exception {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomStateResponse response = roomStateResponse(hostUuid);
        given(flipbookRoomService.startRoom(eq(hostUuid.toString()), eq(ROOM_CODE))).willReturn(response);

        mockMvc
            .perform(post("/api/v1/flipbook/rooms/{roomCode}/start", ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 게임 시작 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE))
            .andExpect(jsonPath("$.data.status").value("PLAYING")).andExpect(jsonPath("$.data.currentRound").value(1))
            .andExpect(jsonPath("$.data.totalRounds").value(4))
            .andExpect(jsonPath("$.data.roundDeadlineAt").isNotEmpty())
            .andExpect(jsonPath("$.data.gameStartedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.viewer.host").value(true));

        verify(flipbookRoomService).startRoom(eq(hostUuid.toString()), eq(ROOM_CODE));
        verify(flipbookRoomEventPublisher).publishGameStarted(response);
    }

    private FlipbookRoomStateResponse roomStateResponse(UUID hostUuid) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomStateResponse(ROOM_CODE, FlipbookRoomStatus.PLAYING, hostUuid.toString(), 45, 2, 6, 2, 1,
            4, now, now.plusSeconds(45), now,
            List.of(new FlipbookRoomParticipantResponse(hostUuid.toString(), "망고", true, 0, true),
                new FlipbookRoomParticipantResponse(UUID.randomUUID().toString(), "다현", false, 1, true)),
            new FlipbookRoomViewerResponse(hostUuid.toString(), true, true, false, false, null), now.minusMinutes(1),
            now);
    }
}
