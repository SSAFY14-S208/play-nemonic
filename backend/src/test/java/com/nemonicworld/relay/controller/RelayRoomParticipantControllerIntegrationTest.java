package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
/**
 * 릴레이 방 입장/복귀 API의 HTTP 계약과 Redis 갱신 경계를 검증합니다.
 */
class RelayRoomParticipantControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "AB3K9Q";
    private static final Duration ROOM_STATE_TTL = Duration.ofHours(24);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void prepare() {
        prepareArtifactTables();
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM app_user");

        valueOperations = createValueOperationsMock();
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    /**
     * 대기 중 방에 자리가 있으면 비참여 사용자를 새 참여자로 추가하고 Redis에 갱신 상태를 저장합니다.
     */
    @Test
    void joinRelayRoomAddsNewParticipantToWaitingRoom() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID joinerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0, true)));
        AppUser beforeUser = userRepository.findById(joinerUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, joinerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("릴레이 방 입장/복귀 성공"))
            .andExpect(jsonPath("$.data.participantCount").value(2))
            .andExpect(jsonPath("$.data.participants[1].userUuid").value(joinerUuid.toString()))
            .andExpect(jsonPath("$.data.participants[1].nickname").value("포도"))
            .andExpect(jsonPath("$.data.participants[1].host").value(false))
            .andExpect(jsonPath("$.data.participants[1].joinOrder").value(1))
            .andExpect(jsonPath("$.data.participants[1].connected").value(true))
            .andExpect(jsonPath("$.data.viewer.userUuid").value(joinerUuid.toString()))
            .andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.host").value(false))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(false))
            .andExpect(jsonPath("$.data.viewer.blockedReason").doesNotExist());

        JsonNode storedRoom = readSavedRoom();
        JsonNode storedJoiner = storedRoom.path("participants").get(1);
        assertThat(storedJoiner.path("userUuid").asText()).isEqualTo(joinerUuid.toString());
        assertThat(storedJoiner.path("nickname").asText()).isEqualTo("포도");
        assertThat(storedJoiner.path("host").asBoolean()).isFalse();
        assertThat(storedJoiner.path("joinOrder").asInt()).isEqualTo(1);
        assertThat(storedJoiner.path("connected").asBoolean()).isTrue();
        assertThat(storedJoiner.path("disconnectedAt").isNull()).isTrue();
        assertThat(storedJoiner.path("joinedAt").asText()).isNotBlank();
        assertThat(storedRoom.path("updatedAt").asText()).isNotEqualTo(storedRoom.path("createdAt").asText());

        AppUser afterUser = userRepository.findById(joinerUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(countRows("relay_drawing_artifact")).isZero();
    }

    /**
     * 이미 연결 중인 기존 참여자의 재호출은 중복 추가와 Redis 저장 없이 성공으로 처리합니다.
     */
    @Test
    void joinRelayRoomReturnsCurrentStateForConnectedParticipantWithoutSaving() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.PLAYING, participant(hostUuid, "망고", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.participantCount").value(1))
            .andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(false));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 연결이 끊긴 기존 참여자가 10초 이내에 호출하면 connected 상태만 복구하고 기존 표시 정보는 유지합니다.
     */
    @Test
    void joinRelayRoomReconnectsDisconnectedParticipantWithinGracePeriod() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        LocalDateTime joinedAt = LocalDateTime.now().minusMinutes(3).truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant disconnectedParticipant = participant(hostUuid, "예전닉", true, 0, false,
            LocalDateTime.now().minusSeconds(3).truncatedTo(ChronoUnit.SECONDS), joinedAt);
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.PLAYING, disconnectedParticipant));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(false))
            .andExpect(jsonPath("$.data.participants[0].nickname").value("예전닉"))
            .andExpect(jsonPath("$.data.participants[0].host").value(true))
            .andExpect(jsonPath("$.data.participants[0].joinOrder").value(0))
            .andExpect(jsonPath("$.data.participants[0].connected").value(true));

        JsonNode storedParticipant = readSavedRoom().path("participants").get(0);
        assertThat(storedParticipant.path("connected").asBoolean()).isTrue();
        assertThat(storedParticipant.path("disconnectedAt").isNull()).isTrue();
        assertThat(storedParticipant.path("nickname").asText()).isEqualTo("예전닉");
        assertThat(storedParticipant.path("host").asBoolean()).isTrue();
        assertThat(storedParticipant.path("joinOrder").asInt()).isZero();
        assertThat(storedParticipant.path("joinedAt").asText()).isEqualTo(joinedAt.toString());
    }

    /**
     * 재접속 유예 시간이 지나면 자동 제출 처리로 보고 Redis 상태를 변경하지 않습니다.
     */
    @Test
    void joinRelayRoomRejectsReconnectAfterGracePeriod() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        RelayRoomParticipant disconnectedParticipant = participant(hostUuid, "망고", true, 0, false,
            LocalDateTime.now().minusSeconds(20).truncatedTo(ChronoUnit.SECONDS));
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.PLAYING, disconnectedParticipant));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, hostUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("이미 자동 제출 처리되었습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 정원이 가득 찬 WAITING 방에는 신규 사용자를 추가하지 않습니다.
     */
    @Test
    void joinRelayRoomRejectsFullWaitingRoom() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID joinerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING,
            participant(hostUuid, "망고", true, 0, true), participant(UUID.randomUUID(), "사과", false, 1, true),
            participant(UUID.randomUUID(), "자두", false, 2, true), participant(UUID.randomUUID(), "키위", false, 3, true),
            participant(UUID.randomUUID(), "배", false, 4, true), participant(UUID.randomUUID(), "감", false, 5, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, joinerUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("방 정원이 가득 찼습니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 게임 진행 중인 방에는 신규 UUID 입장을 차단합니다.
     */
    @Test
    void joinRelayRoomRejectsNewParticipantWhenGameIsPlaying() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID joinerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.PLAYING, participant(hostUuid, "망고", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, joinerUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("게임이 진행 중입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 결과 생성이 끝난 방에는 신규 UUID 입장을 차단합니다.
     */
    @Test
    void joinRelayRoomRejectsNewParticipantWhenRoomIsFinished() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID joinerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.FINISHED, participant(hostUuid, "망고", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, joinerUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("이미 종료된 방입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 닫힌 방에는 신규 UUID 입장을 차단합니다.
     */
    @Test
    void joinRelayRoomRejectsNewParticipantWhenRoomIsClosed() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID joinerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.CLOSED, participant(hostUuid, "망고", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, joinerUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("이미 종료된 방입니다."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * UUID 헤더가 없으면 공통 UUID 오류 메시지로 400을 반환합니다.
     */
    @Test
    void joinRelayRoomRejectsMissingUuidHeader() throws Exception {
        mockMvc.perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * UUID 형식이 잘못되면 새 사용자를 만들지 않고 400을 반환합니다.
     */
    @Test
    void joinRelayRoomRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
    }

    /**
     * UUID 형식은 맞지만 DB에 없는 사용자는 생성하지 않고 404를 반환합니다.
     */
    @Test
    void joinRelayRoomReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
    }

    /**
     * 방코드 형식이 맞지 않으면 Redis 조회 없이 400을 반환합니다.
     */
    @Test
    void joinRelayRoomRejectsInvalidRoomCode() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", "not-a-room")
                .header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("유효하지 않은 방코드입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    /**
     * 방코드 형식은 맞지만 Redis에 방 상태가 없으면 404를 반환합니다.
     */
    @Test
    void joinRelayRoomReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", "ZZZZZZ").header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("존재하지 않는 방입니다."));

        verify(valueOperations).get("relay:room:ZZZZZZ");
        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 신규 입장자의 닉네임이 기본값이면 닉네임 설정 API를 먼저 호출하도록 400을 반환합니다.
     */
    @Test
    void joinRelayRoomRejectsDefaultNicknameForNewParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID joinerUuid = createExistingUserWithDefaultNickname();
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, joinerUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("닉네임을 먼저 설정해주세요."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    /**
     * 신규 입장자의 닉네임이 공백이면 닉네임 설정 API를 먼저 호출하도록 400을 반환합니다.
     */
    @Test
    void joinRelayRoomRejectsBlankNicknameForNewParticipant() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID joinerUuid = createExistingUserWithNickname("   ");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0, true)));

        mockMvc
            .perform(post("/api/v1/relay/rooms/{roomCode}/participants", DEFAULT_ROOM_CODE)
                .header(ANONYMOUS_USER_UUID_HEADER, joinerUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("닉네임을 먼저 설정해주세요."));

        verify(valueOperations, never()).set(anyString(), anyString(), eq(ROOM_STATE_TTL));
    }

    private void prepareArtifactTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS artifact (
                id UUID PRIMARY KEY,
                kind VARCHAR(32) NOT NULL,
                source_room_id VARCHAR(64) NULL,
                thumbnail_url VARCHAR(200) NOT NULL,
                meta VARCHAR(1000) NOT NULL DEFAULT '{}',
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS gallery (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                artifact_id UUID NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS relay_drawing_artifact (
                artifact_id UUID PRIMARY KEY,
                combined_preview_url VARCHAR(200) NULL
            )
            """);
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

    private UUID createExistingUserWithDefaultNickname() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private void storeRoom(String roomCode, RelayRoomState roomState) throws Exception {
        given(valueOperations.get("relay:room:%s".formatted(roomCode)))
            .willReturn(objectMapper.writeValueAsString(roomState));
    }

    private RelayRoomState createRoomState(RelayRoomStatus status, RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomParticipant> participantList = new ArrayList<>(List.of(participants));
        String hostUserUuid = participantList.stream().filter(RelayRoomParticipant::host).findFirst()
            .map(RelayRoomParticipant::userUuid).orElse(participantList.get(0).userUuid());

        return new RelayRoomState(DEFAULT_ROOM_CODE, status, hostUserUuid, 60, 2, 6, null, participantList, createdAt,
            createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected) {
        return participant(userUuid, nickname, host, joinOrder, connected, null);
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected, LocalDateTime disconnectedAt) {
        return participant(userUuid, nickname, host, joinOrder, connected, disconnectedAt,
            LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected, LocalDateTime disconnectedAt, LocalDateTime joinedAt) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, connected, disconnectedAt,
            joinedAt);
    }

    private JsonNode readSavedRoom() throws Exception {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(ROOM_STATE_TTL));

        assertThat(keyCaptor.getValue()).isEqualTo("relay:room:%s".formatted(DEFAULT_ROOM_CODE));

        return objectMapper.readTree(jsonCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM %s".formatted(tableName), Long.class);
    }
}
