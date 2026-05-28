package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.clientlog.config.ClientLogPayloadLimitFilter;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.jwt.AdminJwtAuthenticationFilter;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FlipbookRoomController.class, excludeFilters = @Filter(type = ASSIGNABLE_TYPE, classes = {
    AdminJwtAuthenticationFilter.class, ClientLogPayloadLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiPathPrefixConfig.class)
/**
 * 플립북 방 강퇴 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookRoomKickControllerWebMvcTest {

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
     * POST 요청 본문을 강퇴 요청 DTO로 변환하고 강퇴 이벤트를 발행합니다.
     */
    @Test
    void kickFlipbookRoomParticipantReturnsKickResponseAndPublishesEvents() throws Exception {
        UUID hostUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        FlipbookRoomKickResponse kickResponse = new FlipbookRoomKickResponse(ROOM_CODE, targetUuid.toString(), "포도", 2,
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        given(flipbookRoomService.kickParticipant(eq(hostUuid.toString()), eq(ROOM_CODE), eq(targetUuid.toString())))
            .willReturn(kickResponse);

        mockMvc
            .perform(post("/api/v1/flipbook/rooms/{roomCode}/kick", ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserUuid": "%s"
                    }
                    """.formatted(targetUuid)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("참여자 강퇴 성공")).andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE))
            .andExpect(jsonPath("$.data.kickedUserUuid").value(targetUuid.toString()))
            .andExpect(jsonPath("$.data.kickedNickname").value("포도"))
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.kickedAt").isNotEmpty());

        verify(flipbookRoomService).kickParticipant(eq(hostUuid.toString()), eq(ROOM_CODE), eq(targetUuid.toString()));
        verify(flipbookRoomEventPublisher).publishParticipantKicked(kickResponse);
        verify(flipbookRoomEventPublisher).publishKickedFromRoom(ROOM_CODE, targetUuid.toString());
    }
}
