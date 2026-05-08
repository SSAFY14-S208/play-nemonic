package com.nemonicworld.flipbook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 플립북 대기방 조회 API의 HTTP 응답과 Redis 조회 경계를 검증합니다.
 */
class FlipbookRoomQueryControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "FB3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void prepare() {
        userRepository.deleteAll();

        valueOperations = createValueOperationsMock();
        org.mockito.BDDMockito.given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    /**
     * 참여자인 방장이 조회하면 현재 방 상태와 게임 시작 가능 여부를 함께 반환합니다.
     */
    @Test
    void getFlipbookRoomStateReturnsWaitingRoomForHostViewer() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID participantUuid = createExistingUserWithNickname("다현");
        FlipbookRoomState roomState = waitingRoomState(hostUuid, participantUuid, 6);
        givenStoredRoom(roomState);

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("플립북 방 상태 조회 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.status").value("WAITING"))
            .andExpect(jsonPath("$.data.hostUserUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(45))
            .andExpect(jsonPath("$.data.minParticipants").value(2))
            .andExpect(jsonPath("$.data.maxParticipants").value(6))
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.participants[0].userUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.participants[0].nickname").value("망고"))
            .andExpect(jsonPath("$.data.participants[0].host").value(true))
            .andExpect(jsonPath("$.data.participants[0].joinOrder").value(0))
            .andExpect(jsonPath("$.data.participants[1].userUuid").value(participantUuid.toString()))
            .andExpect(jsonPath("$.data.participants[1].nickname").value("다현"))
            .andExpect(jsonPath("$.data.participants[1].host").value(false))
            .andExpect(jsonPath("$.data.participants[1].joinOrder").value(1))
            .andExpect(jsonPath("$.data.viewer.userUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.host").value(true))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.canStart").value(true)).andExpect(jsonPath("$.data.createdAt").exists())
            .andExpect(jsonPath("$.data.updatedAt").exists());

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 아직 참여하지 않은 사용자가 정원이 남은 대기방을 조회하면 신규 입장 가능 상태를 반환합니다.
     */
    @Test
    void getFlipbookRoomStateReturnsJoinableViewerForNonParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID viewerUuid = createExistingUserWithNickname("다현");
        FlipbookRoomState roomState = waitingRoomState(hostUuid, 6);
        givenStoredRoom(roomState);

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.userUuid").value(viewerUuid.toString()))
            .andExpect(jsonPath("$.data.viewer.participant").value(false))
            .andExpect(jsonPath("$.data.viewer.host").value(false))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(true))
            .andExpect(jsonPath("$.data.viewer.canStart").value(false));
    }

    /**
     * 정원이 찬 방을 비참여자가 조회하면 입장 차단 사유를 함께 반환합니다.
     */
    @Test
    void getFlipbookRoomStateReturnsRoomFullBlockedReasonForNonParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID viewerUuid = createExistingUserWithNickname("다현");
        FlipbookRoomState roomState = waitingRoomState(hostUuid, 1);
        givenStoredRoom(roomState);

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.participant").value(false))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.blockedReason").value("ROOM_FULL"));
    }

    /**
     * UUID 헤더가 없으면 기존 공통 UUID 오류 메시지로 400 응답을 반환합니다.
     */
    @Test
    void getFlipbookRoomStateRejectsMissingUuidHeader() throws Exception {
        mockMvc.perform(get("/api/v1/flipbook/rooms/{roomCode}", DEFAULT_ROOM_CODE)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    /**
     * UUID 형식이 잘못되면 Redis 조회 없이 400 응답을 반환합니다.
     */
    @Test
    void getFlipbookRoomStateRejectsInvalidUuidFormat() throws Exception {
        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    /**
     * 방코드 형식이 잘못되면 Redis 조회 전에 400 응답을 반환합니다.
     */
    @Test
    void getFlipbookRoomStateRejectsInvalidRoomCode() throws Exception {
        UUID viewerUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/not-a-room").header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 방코드입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    /**
     * Redis에 방 상태가 없으면 404 응답을 반환합니다.
     */
    @Test
    void getFlipbookRoomStateReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID viewerUuid = createExistingUserWithNickname("망고");
        org.mockito.BDDMockito.given(valueOperations.get("flipbook:room:%s".formatted(DEFAULT_ROOM_CODE)))
            .willReturn(null);

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 방입니다."));
    }

    /**
     * UUID 형식은 맞지만 사용자가 없으면 404 응답을 반환하고 Redis를 조회하지 않습니다.
     */
    @Test
    void getFlipbookRoomStateReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(get("/api/v1/flipbook/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    private void givenStoredRoom(FlipbookRoomState roomState) throws JsonProcessingException {
        org.mockito.BDDMockito.given(valueOperations.get("flipbook:room:%s".formatted(roomState.roomCode())))
            .willReturn(objectMapper.writeValueAsString(roomState));
    }

    private FlipbookRoomState waitingRoomState(UUID hostUuid, int maxParticipants) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomParticipant hostParticipant = new FlipbookRoomParticipant(hostUuid.toString(), "망고", true, 0, true,
            null, now);

        return new FlipbookRoomState(DEFAULT_ROOM_CODE, FlipbookRoomStatus.WAITING, hostUuid.toString(), 45, 2,
            maxParticipants, List.of(hostParticipant), now, now);
    }

    private FlipbookRoomState waitingRoomState(UUID hostUuid, UUID participantUuid, int maxParticipants) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomParticipant hostParticipant = new FlipbookRoomParticipant(hostUuid.toString(), "망고", true, 0, true,
            null, now);
        FlipbookRoomParticipant participant = new FlipbookRoomParticipant(participantUuid.toString(), "다현", false, 1,
            true, null, now);

        // 저장 순서를 의도적으로 뒤집어 응답이 joinOrder 기준으로 정렬되는지 검증합니다.
        return new FlipbookRoomState(DEFAULT_ROOM_CODE, FlipbookRoomStatus.WAITING, hostUuid.toString(), 45, 2,
            maxParticipants, List.of(participant, hostParticipant), now, now);
    }

    private UUID createExistingUserWithNickname(String nickname) {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime updatedAt = createdAt.plusHours(1);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname(nickname, updatedAt);
        userRepository.saveAndFlush(appUser);

        return userUuid;
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}
