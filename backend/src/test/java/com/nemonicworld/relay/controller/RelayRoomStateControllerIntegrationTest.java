package com.nemonicworld.relay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.support.AbstractIntegrationTest;
import com.nemonicworld.support.AdminUserTestFixture;
import com.nemonicworld.support.AppUserTestFixture;
import com.nemonicworld.support.ArtifactGalleryTestFixture;
import com.nemonicworld.support.BackofficeSettingTestFixture;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 릴레이 방 상태 조회 API의 HTTP 계약과 읽기 전용 동작을 검증합니다.
 */
class RelayRoomStateControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String DEFAULT_ROOM_CODE = "AB3K9Q";

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
    private BackofficeSettingTestFixture backofficeSettingFixture;

    @BeforeEach
    void prepare() {
        new ArtifactGalleryTestFixture(jdbcTemplate).resetRelayArtifactTables();
        prepareBackofficeSettingTables();
        backofficeSettingFixture.deleteAll();
        new AppUserTestFixture(jdbcTemplate).deleteAll();

        valueOperations = createValueOperationsMock();
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    /**
     * 참여자가 자신의 방을 조회하면 참여자/방장 viewer 상태와 Redis 스냅샷이 그대로 반환됩니다.
     */
    @Test
    void getRelayRoomStateReturnsRoomSnapshotAndParticipantViewer() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        RelayRoomState roomState = createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0, true));
        storeRoom(DEFAULT_ROOM_CODE, roomState);
        AppUser beforeUser = userRepository.findById(hostUuid).orElseThrow();
        LocalDateTime beforeLastSeenAt = beforeUser.getLastSeenAt();
        LocalDateTime beforeUpdatedAt = beforeUser.getUpdatedAt();
        String beforeUserAgent = beforeUser.getUserAgent();

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("릴레이 방 상태 조회 성공"))
            .andExpect(jsonPath("$.data.roomCode").value(DEFAULT_ROOM_CODE))
            .andExpect(jsonPath("$.data.status").value("WAITING"))
            .andExpect(jsonPath("$.data.hostUserUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.timeLimitSeconds").value(60))
            .andExpect(jsonPath("$.data.timeLimitDefaultSeconds").value(45))
            .andExpect(jsonPath("$.data.timeLimitAllowedSeconds[0]").value(30))
            .andExpect(jsonPath("$.data.timeLimitAllowedSeconds[1]").value(45))
            .andExpect(jsonPath("$.data.timeLimitAllowedSeconds[2]").value(60))
            .andExpect(jsonPath("$.data.reconnectGraceSeconds").value(10))
            .andExpect(jsonPath("$.data.minParticipants").value(2))
            .andExpect(jsonPath("$.data.maxParticipants").value(6))
            .andExpect(jsonPath("$.data.participantCount").value(1))
            .andExpect(jsonPath("$.data.currentPart").doesNotExist())
            .andExpect(jsonPath("$.data.participants[0].userUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.participants[0].nickname").value("망고"))
            .andExpect(jsonPath("$.data.participants[0].host").value(true))
            .andExpect(jsonPath("$.data.participants[0].joinOrder").value(0))
            .andExpect(jsonPath("$.data.participants[0].connected").value(true))
            .andExpect(jsonPath("$.data.viewer.userUuid").value(hostUuid.toString()))
            .andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.host").value(true))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(false))
            .andExpect(jsonPath("$.data.viewer.blockedReason").doesNotExist())
            .andExpect(jsonPath("$.data.createdAt").exists()).andExpect(jsonPath("$.data.updatedAt").exists());

        AppUser afterUser = userRepository.findById(hostUuid).orElseThrow();
        assertThat(afterUser.getLastSeenAt()).isEqualTo(beforeLastSeenAt);
        assertThat(afterUser.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
        assertThat(afterUser.getUserAgent()).isEqualTo(beforeUserAgent);
        assertThat(countRows("artifact")).isZero();
        assertThat(countRows("gallery")).isZero();
        assertThat(countRows("relay_drawing_artifact")).isZero();
        verify(valueOperations).get("relay:room:%s".formatted(DEFAULT_ROOM_CODE));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getRelayRoomStateReturnsLatestTimeLimitMetadataWithoutChangingExistingRoomLimit() throws Exception {
        insertRelayRoomTimeLimitSetting("""
            {"default":60,"allowed":[60,90],"unit":"seconds","description":"릴레이 방 그리기 제한 시간"}
            """);
        UUID hostUuid = createExistingUserWithNickname("망고");
        RelayRoomState roomState = createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0, true))
            .withTimeLimitSeconds(45, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.timeLimitSeconds").value(45))
            .andExpect(jsonPath("$.data.timeLimitDefaultSeconds").value(60))
            .andExpect(jsonPath("$.data.timeLimitAllowedSeconds[0]").value(60))
            .andExpect(jsonPath("$.data.timeLimitAllowedSeconds[1]").value(90));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getRelayRoomStateReturnsRuntimeReconnectGraceMetadata() throws Exception {
        insertRelayReconnectGraceSetting("""
            {"value":30,"unit":"seconds","description":"릴레이 진행 중 재연결 유예 시간"}
            """);
        UUID hostUuid = createExistingUserWithNickname("망고");
        RelayRoomState roomState = createRoomState(RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            participant(hostUuid, "망고", true, 0, false));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PLAYING"))
            .andExpect(jsonPath("$.data.reconnectGraceSeconds").value(30));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 대기실에 자리가 남아 있으면 비참여자는 신규 입장 가능 상태로 표시됩니다.
     */
    @Test
    void getRelayRoomStateAllowsNonParticipantToJoinWaitingRoomWithCapacity() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID viewerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, participant(hostUuid, "망고", true, 0, true)));

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.participant").value(false))
            .andExpect(jsonPath("$.data.viewer.host").value(false))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(true))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(false))
            .andExpect(jsonPath("$.data.viewer.blockedReason").doesNotExist());

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 대기실 정원이 가득 차면 신규 입장은 ROOM_FULL로 차단됩니다.
     */
    @Test
    void getRelayRoomStateBlocksJoinWhenRoomIsFull() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID viewerUuid = createExistingUserWithNickname("포도");
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.WAITING,
            participant(hostUuid, "망고", true, 0, true), participant(UUID.randomUUID(), "사과", false, 1, true),
            participant(UUID.randomUUID(), "자두", false, 2, true), participant(UUID.randomUUID(), "키위", false, 3, true),
            participant(UUID.randomUUID(), "배", false, 4, true), participant(UUID.randomUUID(), "감", false, 5, true)));

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.participantCount").value(6))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.blockedReason").value("ROOM_FULL"));
    }

    /**
     * 게임 진행 중에는 기존 참여자가 아닌 UUID의 신규 입장을 차단합니다.
     */
    @Test
    void getRelayRoomStateBlocksNonParticipantWhenGameIsPlaying() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        UUID viewerUuid = createExistingUserWithNickname("포도");
        RelayRoomState roomState = createRoomState(RelayRoomStatus.PLAYING, RelayDrawingPart.FACE,
            participant(hostUuid, "망고", true, 0, true));
        storeRoom(DEFAULT_ROOM_CODE, roomState);

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPart").value("FACE"))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.blockedReason").value("GAME_IN_PROGRESS"));
    }

    /**
     * 기존 참여자가 연결 해제 후 10초 유예 시간 안에 조회하면 재접속 가능 상태가 됩니다.
     */
    @Test
    void getRelayRoomStateAllowsReconnectWithinGracePeriod() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        RelayRoomParticipant disconnectedParticipant = participant(hostUuid, "망고", true, 0, false,
            LocalDateTime.now().minusSeconds(3).truncatedTo(ChronoUnit.SECONDS));
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.PLAYING, disconnectedParticipant));

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.canJoin").value(false))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(true))
            .andExpect(jsonPath("$.data.viewer.blockedReason").doesNotExist());
    }

    /**
     * 재접속 유예 시간이 지난 기존 참여자는 RECONNECT_EXPIRED 상태로 안내됩니다.
     */
    @Test
    void getRelayRoomStateBlocksReconnectAfterGracePeriod() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        RelayRoomParticipant disconnectedParticipant = participant(hostUuid, "망고", true, 0, false,
            LocalDateTime.now().minusSeconds(20).truncatedTo(ChronoUnit.SECONDS));
        storeRoom(DEFAULT_ROOM_CODE, createRoomState(RelayRoomStatus.PLAYING, disconnectedParticipant));

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.participant").value(true))
            .andExpect(jsonPath("$.data.viewer.canReconnect").value(false))
            .andExpect(jsonPath("$.data.viewer.blockedReason").value("RECONNECT_EXPIRED"));
    }

    /**
     * Redis에 저장된 참여자 순서와 무관하게 응답은 joinOrder 오름차순으로 정렬됩니다.
     */
    @Test
    void getRelayRoomStateSortsParticipantsByJoinOrder() throws Exception {
        UUID hostUuid = createExistingUserWithNickname("망고");
        storeRoom(DEFAULT_ROOM_CODE,
            createRoomState(RelayRoomStatus.WAITING, participant(UUID.randomUUID(), "자두", false, 2, true),
                participant(hostUuid, "망고", true, 0, true), participant(UUID.randomUUID(), "사과", false, 1, true)));

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                hostUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.participants[0].joinOrder").value(0))
            .andExpect(jsonPath("$.data.participants[1].joinOrder").value(1))
            .andExpect(jsonPath("$.data.participants[2].joinOrder").value(2));
    }

    /**
     * UUID 헤더가 없으면 공통 UUID 오류 메시지로 400을 반환합니다.
     */
    @Test
    void getRelayRoomStateRejectsMissingUuidHeader() throws Exception {
        mockMvc.perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * UUID 형식이 잘못되면 새 사용자를 만들지 않고 400을 반환합니다.
     */
    @Test
    void getRelayRoomStateRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
    }

    /**
     * UUID 형식은 맞지만 DB에 없는 사용자는 생성하지 않고 404를 반환합니다.
     */
    @Test
    void getRelayRoomStateReturnsNotFoundWhenUserDoesNotExistAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", DEFAULT_ROOM_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
        verify(valueOperations, never()).get(anyString());
    }

    /**
     * 방코드 형식이 맞지 않으면 Redis 조회 없이 400을 반환합니다.
     */
    @Test
    void getRelayRoomStateRejectsInvalidRoomCode() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(get("/api/v1/relay/rooms/{roomCode}", "not-a-room").header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 방코드입니다."));

        verify(valueOperations, never()).get(anyString());
    }

    /**
     * 방코드 형식은 맞지만 Redis에 방 상태가 없으면 404를 반환합니다.
     */
    @Test
    void getRelayRoomStateReturnsNotFoundWhenRoomDoesNotExist() throws Exception {
        UUID userUuid = createExistingUserWithNickname("망고");

        mockMvc
            .perform(
                get("/api/v1/relay/rooms/{roomCode}", "ZZZZZZ").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 방입니다."));

        verify(valueOperations).get("relay:room:ZZZZZZ");
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    private void prepareBackofficeSettingTables() {
        new AdminUserTestFixture(jdbcTemplate).ensureTable();
        backofficeSettingFixture = new BackofficeSettingTestFixture(jdbcTemplate);
        backofficeSettingFixture.ensureTable();
    }

    private void insertRelayRoomTimeLimitSetting(String settingValue) {
        backofficeSettingFixture.insert(11L, "relay.room_time_limit_seconds", settingValue, 0L);
    }

    private void insertRelayReconnectGraceSetting(String settingValue) {
        backofficeSettingFixture.insert(12L, "relay.reconnect_grace_seconds", settingValue, 0L);
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

    private void storeRoom(String roomCode, RelayRoomState roomState) throws Exception {
        given(valueOperations.get("relay:room:%s".formatted(roomCode)))
            .willReturn(objectMapper.writeValueAsString(roomState));
    }

    private RelayRoomState createRoomState(RelayRoomStatus status, RelayRoomParticipant... participants) {
        return createRoomState(status, null, participants);
    }

    private RelayRoomState createRoomState(RelayRoomStatus status, RelayDrawingPart currentPart,
        RelayRoomParticipant... participants) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomParticipant> participantList = new ArrayList<>(List.of(participants));
        String hostUserUuid = participantList.stream().filter(RelayRoomParticipant::host).findFirst()
            .map(RelayRoomParticipant::userUuid).orElse(participantList.get(0).userUuid());

        return new RelayRoomState(DEFAULT_ROOM_CODE, status, hostUserUuid, 60, 2, 6, currentPart, participantList,
            createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected) {
        return participant(userUuid, nickname, host, joinOrder, connected, null);
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected, LocalDateTime disconnectedAt) {
        LocalDateTime joinedAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, connected, disconnectedAt,
            joinedAt);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM %s".formatted(tableName), Long.class);
    }
}
