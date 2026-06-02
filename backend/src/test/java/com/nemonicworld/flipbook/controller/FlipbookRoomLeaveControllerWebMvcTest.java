package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.clientlog.config.ClientLogPayloadLimitFilter;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.jwt.AdminJwtAuthenticationFilter;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationTriggerService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import com.nemonicworld.global.config.ApiPathPrefixConfig;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FlipbookRoomController.class, excludeFilters = @Filter(type = ASSIGNABLE_TYPE, classes = {
    AdminJwtAuthenticationFilter.class, ClientLogPayloadLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiPathPrefixConfig.class)
/**
 * 플립북 방 자발적 퇴장 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookRoomLeaveControllerWebMvcTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlipbookRoomService flipbookRoomService;

    @MockitoBean
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    @MockitoBean
    private FlipbookRoomFinalizationTriggerService flipbookRoomFinalizationTriggerService;

    /**
     * DELETE 요청을 퇴장 유스케이스로 위임하고 방 전체 이벤트와 세션 종료를 연결합니다.
     */
    @Test
    void leaveFlipbookRoomReturnsLeaveResponseAndPublishesEvents() throws Exception {
        UUID leftUuid = UUID.randomUUID();
        FlipbookRoomLeaveResponse leaveResponse = new FlipbookRoomLeaveResponse(ROOM_CODE, leftUuid.toString(), "포도", 1,
            false, null, null, false, FlipbookRoomStatus.WAITING, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        given(flipbookRoomService.leaveRoom(eq(leftUuid.toString()), eq(ROOM_CODE))).willReturn(leaveResponse);

        mockMvc
            .perform(delete("/api/v1/flipbook/rooms/{roomCode}/participants/me", ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, leftUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 방 퇴장 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE))
            .andExpect(jsonPath("$.data.leftUserUuid").value(leftUuid.toString()))
            .andExpect(jsonPath("$.data.leftNickname").value("포도"))
            .andExpect(jsonPath("$.data.participantCount").value(1))
            .andExpect(jsonPath("$.data.hostChanged").value(false))
            .andExpect(jsonPath("$.data.roomClosed").value(false))
            .andExpect(jsonPath("$.data.roomStatus").value("WAITING"))
            .andExpect(jsonPath("$.data.leftAt").isNotEmpty());

        verify(flipbookRoomService).leaveRoom(eq(leftUuid.toString()), eq(ROOM_CODE));
        verify(flipbookRoomEventPublisher).publishParticipantLeft(leaveResponse);
        verify(flipbookRoomEventPublisher, never()).publishHostChanged(leaveResponse);
        verify(flipbookRoomEventPublisher, never()).publishRoomClosed(eq(ROOM_CODE), eq(leaveResponse.leftAt()));
        verify(flipbookRoomEventPublisher).closeLeftRoomSession(ROOM_CODE, leftUuid.toString());
    }

    /**
     * 방장 퇴장 응답이면 방장 변경 이벤트도 발행합니다.
     */
    @Test
    void leaveFlipbookRoomPublishesHostChangedWhenHostLeaves() throws Exception {
        UUID leftUuid = UUID.randomUUID();
        UUID newHostUuid = UUID.randomUUID();
        FlipbookRoomLeaveResponse leaveResponse = new FlipbookRoomLeaveResponse(ROOM_CODE, leftUuid.toString(), "망고", 1,
            true, newHostUuid.toString(), "포도", false, FlipbookRoomStatus.WAITING,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        given(flipbookRoomService.leaveRoom(eq(leftUuid.toString()), eq(ROOM_CODE))).willReturn(leaveResponse);

        mockMvc.perform(delete("/api/v1/flipbook/rooms/{roomCode}/participants/me", ROOM_CODE)
            .header(ANONYMOUS_USER_UUID_HEADER, leftUuid.toString())).andExpect(status().isOk());

        verify(flipbookRoomEventPublisher).publishParticipantLeft(leaveResponse);
        verify(flipbookRoomEventPublisher).publishHostChanged(leaveResponse);
        verify(flipbookRoomEventPublisher, never()).publishRoomClosed(eq(ROOM_CODE), eq(leaveResponse.leftAt()));
        verify(flipbookRoomEventPublisher).closeLeftRoomSession(ROOM_CODE, leftUuid.toString());
    }
}
