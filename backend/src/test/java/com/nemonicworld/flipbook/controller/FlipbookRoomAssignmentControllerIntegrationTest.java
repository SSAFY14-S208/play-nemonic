package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomMyAssignmentResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import com.nemonicworld.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
 * 플립북 내 현재 프레임 배정 조회 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookRoomAssignmentControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlipbookRoomService flipbookRoomService;

    @MockitoBean
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    /**
     * GET 요청을 내 현재 프레임 배정 조회 유스케이스로 위임합니다.
     */
    @Test
    void getMyAssignmentReturnsCurrentFrameAssignment() throws Exception {
        UUID participantUuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomMyAssignmentResponse response = new FlipbookRoomMyAssignmentResponse(ROOM_CODE, 1, 4, 1, 0,
            FlipbookFrameAssignmentStatus.PENDING, 45, now, now.plusSeconds(45), 45, null);
        given(flipbookRoomService.getMyAssignment(eq(participantUuid.toString()), eq(ROOM_CODE))).willReturn(response);

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}/assignments/me", ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, participantUuid.toString()).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("내 플립북 프레임 배정 조회 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE)).andExpect(jsonPath("$.data.currentRound").value(1))
            .andExpect(jsonPath("$.data.totalRounds").value(4)).andExpect(jsonPath("$.data.flipbookIndex").value(1))
            .andExpect(jsonPath("$.data.frameIndex").value(0))
            .andExpect(jsonPath("$.data.assignmentStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(45))
            .andExpect(jsonPath("$.data.remainingSeconds").value(45)).andExpect(jsonPath("$.data.hint").doesNotExist());

        verify(flipbookRoomService).getMyAssignment(eq(participantUuid.toString()), eq(ROOM_CODE));
    }
}
