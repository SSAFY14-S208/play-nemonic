package com.nemonicworld.flipbook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomParticipantResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomViewerResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * 플립북 방 설정 변경 API의 HTTP 요청/응답 연결을 검증합니다.
 */
class FlipbookRoomSettingsControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlipbookRoomService flipbookRoomService;

    /**
     * PATCH 요청 본문을 설정 변경 요청 DTO로 변환하고 최신 방 상태 응답을 반환합니다.
     */
    @Test
    void updateFlipbookRoomSettingsReturnsUpdatedRoomState() throws Exception {
        UUID hostUuid = UUID.randomUUID();
        given(flipbookRoomService.updateRoomSettings(eq(hostUuid.toString()), eq(ROOM_CODE),
            org.mockito.ArgumentMatchers.any(FlipbookRoomSettingsRequest.class)))
            .willReturn(roomStateResponse(hostUuid, 60));

        mockMvc
            .perform(patch("/api/v1/flipbook/rooms/{roomCode}/settings", ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "timeLimitSeconds": 60
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 방 설정 변경 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(ROOM_CODE))
            .andExpect(jsonPath("$.data.status").value("WAITING"))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(60))
            .andExpect(jsonPath("$.data.viewer.host").value(true));

        ArgumentCaptor<FlipbookRoomSettingsRequest> requestCaptor = ArgumentCaptor
            .forClass(FlipbookRoomSettingsRequest.class);
        verify(flipbookRoomService).updateRoomSettings(eq(hostUuid.toString()), eq(ROOM_CODE), requestCaptor.capture());
        assertThat(requestCaptor.getValue().timeLimitSeconds()).isEqualTo(60);
    }

    private FlipbookRoomStateResponse roomStateResponse(UUID hostUuid, int timeLimitSeconds) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomStateResponse(ROOM_CODE, FlipbookRoomStatus.WAITING, hostUuid.toString(),
            timeLimitSeconds, 2, 6, 1,
            List.of(new FlipbookRoomParticipantResponse(hostUuid.toString(), "망고", true, 0, true)),
            new FlipbookRoomViewerResponse(hostUuid.toString(), true, true, false, false, null), now, now);
    }
}
