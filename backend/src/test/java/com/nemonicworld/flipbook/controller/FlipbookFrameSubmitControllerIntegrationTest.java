package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * 플립북 프레임 제출 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookFrameSubmitControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlipbookRoomService flipbookRoomService;

    @MockitoBean
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    /**
     * POST 요청을 프레임 제출 유스케이스로 위임하고 제출 이벤트를 발행합니다.
     */
    @Test
    void submitFrameReturnsSubmissionProgressAndPublishesEvent() throws Exception {
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookFrameSubmitRequest request = new FlipbookFrameSubmitRequest(1, 2, fileId.toString());
        FlipbookFrameSubmitResponse response = new FlipbookFrameSubmitResponse(ROOM_CODE, 2, 1, 2,
            FlipbookFrameAssignmentStatus.SUBMITTED, fileId.toString(),
            "uploads/flipbook/2026/05/08/%s/frame.png".formatted(fileId),
            "https://example.com/minio/nemonic/uploads/flipbook/2026/05/08/%s/frame.png".formatted(fileId), now, false,
            true, 2, 2, true, 3, now, now.plusSeconds(45), false, FlipbookRoomStatus.PLAYING,
            participantUuid.toString(), "망고");
        given(flipbookRoomService.submitFrame(eq(participantUuid.toString()), eq(ROOM_CODE), eq(2), eq(request)))
            .willReturn(response);

        mockMvc
            .perform(post("/api/v1/flipbook/rooms/{roomCode}/rounds/{round}/frames", ROOM_CODE, 2)
                .header(ANONYMOUS_USER_UUID_HEADER, participantUuid.toString()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "flipbookIndex": 1,
                      "frameIndex": 2,
                      "fileId": "%s"
                    }
                    """.formatted(fileId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 프레임 제출 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE)).andExpect(jsonPath("$.data.round").value(2))
            .andExpect(jsonPath("$.data.flipbookIndex").value(1)).andExpect(jsonPath("$.data.frameIndex").value(2))
            .andExpect(jsonPath("$.data.assignmentStatus").value("SUBMITTED"))
            .andExpect(jsonPath("$.data.fileId").value(fileId.toString()))
            .andExpect(jsonPath("$.data.currentRoundCompleted").value(true))
            .andExpect(jsonPath("$.data.advanced").value(true)).andExpect(jsonPath("$.data.nextRound").value(3))
            .andExpect(jsonPath("$.data.roomStatus").value("PLAYING"));

        verify(flipbookRoomService).submitFrame(eq(participantUuid.toString()), eq(ROOM_CODE), eq(2), eq(request));
        verify(flipbookRoomEventPublisher).publishFrameSubmitted(response);
        verify(flipbookRoomEventPublisher).publishRoundStarted(ROOM_CODE, 2, 3, now, now.plusSeconds(45));
    }

    /**
     * 마지막 라운드 제출로 전체 라운드가 완료되면 전체 완료 이벤트를 함께 발행합니다.
     */
    @Test
    void submitFramePublishesAllRoundsCompletedWhenLastRoundFinishes() throws Exception {
        UUID participantUuid = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookFrameSubmitRequest request = new FlipbookFrameSubmitRequest(1, 3, fileId.toString());
        FlipbookFrameSubmitResponse response = new FlipbookFrameSubmitResponse(ROOM_CODE, 3, 1, 3,
            FlipbookFrameAssignmentStatus.SUBMITTED, fileId.toString(),
            "uploads/flipbook/2026/05/08/%s/frame.png".formatted(fileId),
            "https://example.com/minio/nemonic/uploads/flipbook/2026/05/08/%s/frame.png".formatted(fileId), now, false,
            true, 2, 2, true, null, null, null, true, FlipbookRoomStatus.FINALIZING, participantUuid.toString(), "망고");
        given(flipbookRoomService.submitFrame(eq(participantUuid.toString()), eq(ROOM_CODE), eq(3), eq(request)))
            .willReturn(response);

        mockMvc
            .perform(post("/api/v1/flipbook/rooms/{roomCode}/rounds/{round}/frames", ROOM_CODE, 3)
                .header(ANONYMOUS_USER_UUID_HEADER, participantUuid.toString()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "flipbookIndex": 1,
                      "frameIndex": 3,
                      "fileId": "%s"
                    }
                    """.formatted(fileId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.advanced").value(true))
            .andExpect(jsonPath("$.data.allRoundsCompleted").value(true))
            .andExpect(jsonPath("$.data.roomStatus").value("FINALIZING"));

        verify(flipbookRoomService).submitFrame(eq(participantUuid.toString()), eq(ROOM_CODE), eq(3), eq(request));
        verify(flipbookRoomEventPublisher).publishFrameSubmitted(response);
        verify(flipbookRoomEventPublisher).publishAllRoundsCompleted(ROOM_CODE, FlipbookRoomStatus.FINALIZING, now);
    }
}
