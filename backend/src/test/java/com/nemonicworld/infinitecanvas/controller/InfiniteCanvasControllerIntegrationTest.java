package com.nemonicworld.infinitecanvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperationType;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasParticipant;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasState;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import com.nemonicworld.invite.redis.InviteMetadata;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@SuppressWarnings({"unchecked", "rawtypes"})
class InfiniteCanvasControllerIntegrationTest {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String INVITE_CODE = "IC3K9Q";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InfiniteCanvasService infiniteCanvasService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private RoomCodeGenerator roomCodeGenerator;

    private RedisOperations<String, String> redisOperations;
    private ValueOperations<String, String> valueOperations;
    private Map<String, String> redisValues;

    @BeforeEach
    void prepare() {
        prepareBackofficeSettingTables();
        jdbcTemplate.update("DELETE FROM backoffice_setting");
        userRepository.deleteAll();

        redisValues = new LinkedHashMap<>();
        redisOperations = createRedisOperationsMock();
        valueOperations = createValueOperationsMock();

        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisOperations.opsForValue()).willReturn(valueOperations);
        given(redisOperations.exec()).willReturn(List.of("OK"));
        given(stringRedisTemplate.execute(any(SessionCallback.class))).willAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);

            return callback.execute(redisOperations);
        });
        given(stringRedisTemplate.hasKey(anyString()))
            .willAnswer(invocation -> redisValues.containsKey(invocation.getArgument(0, String.class)));
        given(valueOperations.get(anyString()))
            .willAnswer(invocation -> redisValues.get(invocation.getArgument(0, String.class)));
        doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any());
        doAnswer(invocation -> redisValues.remove(invocation.getArgument(0, String.class)) != null)
            .when(stringRedisTemplate).delete(anyString());
        given(roomCodeGenerator.generateUnique(any())).willReturn(INVITE_CODE);
    }

    @Test
    void createInfiniteCanvasReturnsCreatedResponseAndStoresCanvasAndInvite() throws Exception {
        UUID userUuid = createExistingUserWithNickname("다현");

        MvcResult result = mockMvc
            .perform(post("/api/v1/infinite-canvas/canvases").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType("application/json").content("""
                    {
                      "nickname": "햇살",
                      "color": "#72DDF7",
                      "avatarUrl": "https://example.com/avatar.png",
                      "viewport": {
                        "x": 10,
                        "y": 20,
                        "zoom": 0.8
                      }
                    }
                    """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("무한 캔버스 생성 성공"))
            .andExpect(jsonPath("$.data.inviteCode").value(INVITE_CODE))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.ownerUserUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.me.userUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.me.nickname").value("햇살"))
            .andExpect(jsonPath("$.data.me.color").value("#72DDF7"))
            .andExpect(jsonPath("$.data.me.connected").value(false))
            .andExpect(jsonPath("$.data.participants.length()").value(1))
            .andExpect(jsonPath("$.data.elements.length()").value(0))
            .andExpect(jsonPath("$.data.maxParticipants").value(6)).andExpect(jsonPath("$.data.revision").value(0))
            .andReturn();

        JsonNode responseData = readData(result);
        String canvasId = responseData.path("canvasId").asText();
        JsonNode storedCanvas = readStoredJson(canvasKey(canvasId));
        JsonNode storedInvite = readStoredJson("invite:" + INVITE_CODE);

        assertThat(storedCanvas.path("canvasId").asText()).isEqualTo(canvasId);
        assertThat(storedCanvas.path("inviteCode").asText()).isEqualTo(INVITE_CODE);
        assertThat(storedCanvas.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(storedCanvas.path("participants")).hasSize(1);
        assertThat(storedCanvas.path("participants").get(0).path("nickname").asText()).isEqualTo("햇살");
        assertThat(storedCanvas.path("participants").get(0).path("connected").asBoolean()).isFalse();
        assertThat(storedCanvas.path("viewport").path("zoom").asDouble()).isEqualTo(0.8);
        assertThat(storedInvite.path("inviteCode").asText()).isEqualTo(INVITE_CODE);
        assertThat(storedInvite.path("boothType").asText()).isEqualTo("infinite_canvas");
        assertThat(storedInvite.path("roomId").asText()).isEqualTo(canvasId);
        assertThat(storedInvite.path("roomName").asText()).isEqualTo("햇살의 무한 캔버스");
        assertThat(storedInvite.path("expiresAt").asText()).isNotBlank();
    }

    @Test
    void createInfiniteCanvasUsesRuntimeParticipantLimit() throws Exception {
        insertInfiniteCanvasParticipantLimitSetting("""
            {"min":1,"max":8,"unit":"people","description":"Infinite canvas participant limit"}
            """);
        UUID userUuid = createExistingUserWithNickname("Mango");

        MvcResult result = mockMvc
            .perform(post("/api/v1/infinite-canvas/canvases").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.maxParticipants").value(8)).andReturn();

        String canvasId = readData(result).path("canvasId").asText();
        assertThat(readStoredJson(canvasKey(canvasId)).path("maxParticipants").asInt()).isEqualTo(8);
    }

    @Test
    void getInfiniteCanvasAutoAddsRequesterWhenNotParticipant() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        UUID viewerUuid = createExistingUserWithNickname("Viewer");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        mockMvc
            .perform(get("/api/v1/infinite-canvas/canvases/{canvasId}", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("무한 캔버스 조회 성공"))
            .andExpect(jsonPath("$.data.canvasId").value(state.canvasId()))
            .andExpect(jsonPath("$.data.me.userUuid").value(viewerUuid.toString()))
            .andExpect(jsonPath("$.data.me.nickname").value("Viewer"))
            .andExpect(jsonPath("$.data.participants.length()").value(2));

        JsonNode storedCanvas = readStoredJson(canvasKey(state.canvasId()));
        JsonNode storedInvite = readStoredJson("invite:" + state.inviteCode());
        assertThat(storedCanvas.path("participants")).hasSize(2);
        assertThat(storedCanvas.path("participants").get(1).path("userUuid").asText()).isEqualTo(viewerUuid.toString());
        assertThat(storedCanvas.path("participants").get(1).path("connected").asBoolean()).isFalse();
        assertThat(storedInvite.path("boothType").asText()).isEqualTo("infinite_canvas");
        assertThat(storedInvite.path("roomId").asText()).isEqualTo(state.canvasId());
    }

    @Test
    void getInfiniteCanvasDoesNotDuplicateExistingParticipant() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        UUID viewerUuid = createExistingUserWithNickname("Viewer");
        InfiniteCanvasParticipant owner = participant(ownerUuid, "Owner");
        InfiniteCanvasParticipant viewer = participant(viewerUuid, "Viewer");
        InfiniteCanvasState state = activeCanvasState(6, owner, viewer);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        mockMvc
            .perform(get("/api/v1/infinite-canvas/canvases/{canvasId}", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.me.userUuid").value(viewerUuid.toString()))
            .andExpect(jsonPath("$.data.participants.length()").value(2));

        assertThat(readStoredJson(canvasKey(state.canvasId())).path("participants")).hasSize(2);
    }

    @Test
    void getInfiniteCanvasRejectsFullCanvas() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        UUID viewerUuid = createExistingUserWithNickname("Viewer");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 1);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        mockMvc
            .perform(get("/api/v1/infinite-canvas/canvases/{canvasId}", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("무한 캔버스 최대 참여자 수를 초과했습니다."));

        assertThat(readStoredJson(canvasKey(state.canvasId())).path("participants")).hasSize(1);
    }

    @Test
    void getInfiniteCanvasRejectsInvalidCanvasId() throws Exception {
        UUID userUuid = createExistingUserWithNickname("Viewer");

        mockMvc
            .perform(get("/api/v1/infinite-canvas/canvases/{canvasId}", "not-a-uuid").header(ANONYMOUS_USER_UUID_HEADER,
                userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 캔버스 ID 형식입니다."));
    }

    @Test
    void updateInfiniteCanvasParticipantProfile() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        mockMvc
            .perform(patch("/api/v1/infinite-canvas/canvases/{canvasId}/participants/me", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, ownerUuid.toString()).contentType("application/json").content("""
                    {
                      "nickname": "새로운 붓",
                      "color": "#F472B6",
                      "avatarUrl": "https://example.com/new-avatar.png"
                    }
                    """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("무한 캔버스 참여자 정보 수정 성공"))
            .andExpect(jsonPath("$.data.userUuid").value(ownerUuid.toString()))
            .andExpect(jsonPath("$.data.nickname").value("새로운 붓")).andExpect(jsonPath("$.data.color").value("#F472B6"))
            .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/new-avatar.png"));

        JsonNode storedParticipant = readStoredJson(canvasKey(state.canvasId())).path("participants").get(0);
        assertThat(storedParticipant.path("nickname").asText()).isEqualTo("새로운 붓");
        assertThat(storedParticipant.path("color").asText()).isEqualTo("#F472B6");
        assertThat(storedParticipant.path("avatarUrl").asText()).isEqualTo("https://example.com/new-avatar.png");
    }

    @Test
    void leaveInfiniteCanvasRemovesParticipantWhenOthersRemain() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        UUID viewerUuid = createExistingUserWithNickname("Viewer");
        InfiniteCanvasParticipant owner = participant(ownerUuid, "Owner");
        InfiniteCanvasParticipant viewer = participant(viewerUuid, "Viewer");
        InfiniteCanvasState state = activeCanvasState(6, owner, viewer);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        mockMvc
            .perform(delete("/api/v1/infinite-canvas/canvases/{canvasId}/participants/me", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("무한 캔버스 퇴장 성공"))
            .andExpect(jsonPath("$.data.canvasId").value(state.canvasId()))
            .andExpect(jsonPath("$.data.userUuid").value(viewerUuid.toString()))
            .andExpect(jsonPath("$.data.closed").value(false));

        JsonNode storedParticipants = readStoredJson(canvasKey(state.canvasId())).path("participants");
        assertThat(storedParticipants).hasSize(1);
        assertThat(storedParticipants.get(0).path("userUuid").asText()).isEqualTo(ownerUuid.toString());
    }

    @Test
    void leaveInfiniteCanvasDeletesCanvasWhenLastParticipantLeaves() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        mockMvc
            .perform(delete("/api/v1/infinite-canvas/canvases/{canvasId}/participants/me", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, ownerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.canvasId").value(state.canvasId()))
            .andExpect(jsonPath("$.data.userUuid").value(ownerUuid.toString()))
            .andExpect(jsonPath("$.data.closed").value(true)).andExpect(jsonPath("$.data.closedAt").isNotEmpty());

        assertThat(redisValues).doesNotContainKey(canvasKey(state.canvasId()));
    }

    @Test
    void joinInviteCodeAddsInfiniteCanvasParticipant() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        UUID viewerUuid = createExistingUserWithNickname("Viewer");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));
        redisValues.put("invite:" + INVITE_CODE, objectMapper.writeValueAsString(new InviteMetadata(INVITE_CODE,
            "infinite_canvas", state.canvasId(), "Owner의 무한 캔버스", LocalDateTime.now().plusHours(1))));

        mockMvc
            .perform(post("/api/v1/invites/{inviteCode}", INVITE_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("방 입장 성공"))
            .andExpect(jsonPath("$.data.boothType").value("infinite_canvas"))
            .andExpect(jsonPath("$.data.roomId").value(state.canvasId()))
            .andExpect(jsonPath("$.data.roomName").value("Owner의 무한 캔버스"))
            .andExpect(jsonPath("$.data.hostNickname").value("Owner"))
            .andExpect(jsonPath("$.data.currentParticipants").value(2))
            .andExpect(jsonPath("$.data.maxParticipants").value(6))
            .andExpect(jsonPath("$.data.yourRole").value("participant"))
            .andExpect(jsonPath("$.data.alreadyJoined").value(false));

        JsonNode storedParticipants = readStoredJson(canvasKey(state.canvasId())).path("participants");
        assertThat(storedParticipants).hasSize(2);
        assertThat(storedParticipants.get(1).path("userUuid").asText()).isEqualTo(viewerUuid.toString());
        assertThat(storedParticipants.get(1).path("nickname").asText()).isEqualTo("Viewer");
        assertThat(readStoredJson("invite:" + INVITE_CODE).path("boothType").asText()).isEqualTo("infinite_canvas");
    }

    @Test
    void applyInfiniteCanvasOperationsStoresAcceptedOperationsAndElements() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));
        JsonNode element = objectMapper.createObjectNode().put("id", "shape-1").put("type", "sticky-note").put("text",
            "hello");

        InfiniteCanvasOpsAppliedResponse response = infiniteCanvasService.applyOperations(ownerUuid.toString(),
            state.canvasId(), new InfiniteCanvasOpsRequest(0L, List.of(new InfiniteCanvasOperationRequest("local-op-1",
                "client-op-1", InfiniteCanvasOperationType.UPSERT_ELEMENT, "shape-1", element, null))));

        JsonNode storedCanvas = readStoredJson(canvasKey(state.canvasId()));
        assertThat(response.canvasId()).isEqualTo(state.canvasId());
        assertThat(response.revision()).isEqualTo(1L);
        assertThat(response.elementCount()).isEqualTo(1);
        assertThat(response.operations()).hasSize(1);
        assertThat(response.operations().getFirst().clientOperationId()).isEqualTo("client-op-1");
        assertThat(storedCanvas.path("revision").asLong()).isEqualTo(1L);
        assertThat(storedCanvas.path("elements")).hasSize(1);
        assertThat(storedCanvas.path("elements").get(0).path("id").asText()).isEqualTo("shape-1");
        assertThat(storedCanvas.path("operations")).hasSize(1);
    }

    @Test
    void applyInfiniteCanvasOperationsRejectsStaleRevision() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6, 2L);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));
        JsonNode element = objectMapper.createObjectNode().put("id", "shape-2").put("type", "brush");

        assertThatThrownBy(() -> infiniteCanvasService.applyOperations(ownerUuid.toString(), state.canvasId(),
            new InfiniteCanvasOpsRequest(1L,
                List.of(new InfiniteCanvasOperationRequest("local-op-2", "client-op-2",
                    InfiniteCanvasOperationType.UPSERT_ELEMENT, "shape-2", element, null)))))
            .isInstanceOf(ConflictException.class).hasMessage("캔버스 revision이 최신이 아닙니다. 서버 상태를 다시 동기화해주세요.");

        assertThat(readStoredJson(canvasKey(state.canvasId())).path("revision").asLong()).isEqualTo(2L);
    }

    @Test
    void replaceInfiniteCanvasSnapshotStoresElementsAndViewport() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));
        JsonNode element = objectMapper.createObjectNode().put("id", "snapshot-note").put("type", "text").put("text",
            "snapshot");
        JsonNode viewport = objectMapper.createObjectNode().put("x", 120).put("y", -80).put("zoom", 0.75);

        InfiniteCanvasStateResponse response = infiniteCanvasService.replaceSnapshot(ownerUuid.toString(),
            state.canvasId(), new InfiniteCanvasSnapshotRequest(0L, List.of(element), viewport));

        JsonNode storedCanvas = readStoredJson(canvasKey(state.canvasId()));
        assertThat(response.revision()).isEqualTo(1L);
        assertThat(response.elements()).hasSize(1);
        assertThat(response.viewport().path("zoom").asDouble()).isEqualTo(0.75);
        assertThat(storedCanvas.path("revision").asLong()).isEqualTo(1L);
        assertThat(storedCanvas.path("elements")).hasSize(1);
        assertThat(storedCanvas.path("elements").get(0).path("id").asText()).isEqualTo("snapshot-note");
        assertThat(storedCanvas.path("viewport").path("x").asInt()).isEqualTo(120);
    }

    private void prepareBackofficeSettingTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS admin_user (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                login_id VARCHAR(64) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                nickname VARCHAR(20) NOT NULL,
                email VARCHAR(255) NOT NULL,
                role VARCHAR(32) NOT NULL,
                last_login_at TIMESTAMP NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS backoffice_setting (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                setting_key VARCHAR(128) NOT NULL UNIQUE,
                setting_value TEXT NOT NULL DEFAULT '{}',
                updated_by BIGINT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);
    }

    private void insertInfiniteCanvasParticipantLimitSetting(String settingValue) {
        LocalDateTime now = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO backoffice_setting (
                id,
                setting_key,
                setting_value,
                updated_by,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """, 10L, "infinite_canvas.participant_limit", settingValue, 0L, Timestamp.valueOf(now),
            Timestamp.valueOf(now));
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

    private InfiniteCanvasState activeCanvasState(UUID ownerUuid, int maxParticipants) {
        return activeCanvasState(ownerUuid, maxParticipants, 0L);
    }

    private InfiniteCanvasState activeCanvasState(UUID ownerUuid, int maxParticipants, long revision) {
        return activeCanvasState(maxParticipants, revision, participant(ownerUuid, "Owner"));
    }

    private InfiniteCanvasState activeCanvasState(int maxParticipants, InfiniteCanvasParticipant... participants) {
        return activeCanvasState(maxParticipants, 0L, participants);
    }

    private InfiniteCanvasState activeCanvasState(int maxParticipants, long revision,
        InfiniteCanvasParticipant... participants) {
        LocalDateTime now = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new InfiniteCanvasState(UUID.randomUUID().toString(), INVITE_CODE, InfiniteCanvasStatus.ACTIVE,
            participants[0].userUuid(), List.of(participants), List.of(), List.of(), Map.of(), Map.of(),
            objectMapper.createObjectNode().put("x", 0).put("y", 0).put("zoom", 1.0), maxParticipants, revision, now,
            now, null);
    }

    private InfiniteCanvasParticipant participant(UUID userUuid, String nickname) {
        LocalDateTime now = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);

        return new InfiniteCanvasParticipant(userUuid.toString(), nickname, "#72DDF7", null, false, now, null, now);
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode readStoredJson(String key) throws Exception {
        String value = redisValues.get(key);
        assertThat(value).as("Redis key %s", key).isNotBlank();

        return objectMapper.readTree(value);
    }

    private String serialize(InfiniteCanvasState state) throws Exception {
        return objectMapper.writeValueAsString(state);
    }

    private String canvasKey(String canvasId) {
        return "infinite-canvas:canvas:" + canvasId;
    }

    private RedisOperations<String, String> createRedisOperationsMock() {
        return (RedisOperations<String, String>) mock(RedisOperations.class);
    }

    private ValueOperations<String, String> createValueOperationsMock() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }
}
