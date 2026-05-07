package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultFrameResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultItemResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultsResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * 플립북 결과 조회 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookRoomResultControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlipbookRoomService flipbookRoomService;

    @MockitoBean
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    /**
     * GET 요청을 결과 조회 유스케이스로 위임하고 GIF/프레임 정보를 반환합니다.
     */
    @Test
    void getResultsReturnsFlipbookGifAndFrames() throws Exception {
        UUID participantUuid = UUID.randomUUID();
        UUID galleryId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomResultFrameResponse frame = new FlipbookRoomResultFrameResponse(0,
            "https://example.com/minio/nemonic/uploads/flipbook/frame.png", participantUuid.toString(), "망고");
        FlipbookRoomResultItemResponse result = new FlipbookRoomResultItemResponse(0, galleryId.toString(),
            artifactId.toString(), "https://example.com/minio/nemonic/flipbook/results/thumbnail.png",
            "https://example.com/minio/nemonic/flipbook/results/result.gif",
            "https://example.com/minio/nemonic/uploads/flipbook/frame.png", now, List.of(frame));
        FlipbookRoomResultsResponse response = new FlipbookRoomResultsResponse(ROOM_CODE, FlipbookRoomStatus.FINISHED,
            true, 1, List.of(result));
        given(flipbookRoomService.getResults(eq(participantUuid.toString()), eq(ROOM_CODE))).willReturn(response);

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}/result", ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                participantUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 결과 조회 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE))
            .andExpect(jsonPath("$.data.roomStatus").value("FINISHED")).andExpect(jsonPath("$.data.ready").value(true))
            .andExpect(jsonPath("$.data.resultCount").value(1))
            .andExpect(jsonPath("$.data.results[0].flipbookIndex").value(0))
            .andExpect(jsonPath("$.data.results[0].galleryId").value(galleryId.toString()))
            .andExpect(jsonPath("$.data.results[0].artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.results[0].gifUrl").value(result.gifUrl()))
            .andExpect(jsonPath("$.data.results[0].firstImageUrl").value(result.firstImageUrl()))
            .andExpect(jsonPath("$.data.results[0].frames[0].frameIndex").value(0))
            .andExpect(jsonPath("$.data.results[0].frames[0].drawnByNickname").value("망고"));
    }
}
