package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomCloseResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import com.nemonicworld.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * 플립북 방 수동 종료 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookRoomCloseControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlipbookRoomService flipbookRoomService;

    @MockitoBean
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    @Test
    void closeFlipbookRoomReturnsClosedResponseAndPublishesEvent() throws Exception {
        UUID hostUuid = UUID.randomUUID();
        LocalDateTime closedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomCloseResponse response = new FlipbookRoomCloseResponse(ROOM_CODE, FlipbookRoomStatus.CLOSED,
            closedAt, false);
        given(flipbookRoomService.closeRoom(eq(hostUuid.toString()), eq(ROOM_CODE))).willReturn(response);

        mockMvc
            .perform(post("/api/v1/flipbook/rooms/{roomCode}/close", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 방 종료 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE))
            .andExpect(jsonPath("$.data.roomStatus").value("CLOSED"))
            .andExpect(jsonPath("$.data.closedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.alreadyClosed").value(false));

        verify(flipbookRoomService).closeRoom(eq(hostUuid.toString()), eq(ROOM_CODE));
        verify(flipbookRoomEventPublisher).publishRoomClosed(ROOM_CODE, closedAt);
    }

    @Test
    void closeAlreadyClosedFlipbookRoomDoesNotPublishEventAgain() throws Exception {
        UUID hostUuid = UUID.randomUUID();
        LocalDateTime closedAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomCloseResponse response = new FlipbookRoomCloseResponse(ROOM_CODE, FlipbookRoomStatus.CLOSED,
            closedAt, true);
        given(flipbookRoomService.closeRoom(eq(hostUuid.toString()), eq(ROOM_CODE))).willReturn(response);

        mockMvc
            .perform(post("/api/v1/flipbook/rooms/{roomCode}/close", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("이미 종료된 방입니다."))
            .andExpect(jsonPath("$.data.roomStatus").value("CLOSED"))
            .andExpect(jsonPath("$.data.alreadyClosed").value(true));

        verify(flipbookRoomService).closeRoom(eq(hostUuid.toString()), eq(ROOM_CODE));
        verify(flipbookRoomEventPublisher, never()).publishRoomClosed(eq(ROOM_CODE), eq(closedAt));
    }
}
