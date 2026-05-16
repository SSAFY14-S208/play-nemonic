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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasLockRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOperationRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLockResponse;
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
    private static final String MINIO_PUBLIC_URL = "http://localhost:9000/nemonic-local/";

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
        prepareOutputTables();
        jdbcTemplate.update("DELETE FROM community_memo");
        jdbcTemplate.update("DELETE FROM fortune_artifact");
        jdbcTemplate.update("DELETE FROM relay_drawing_artifact");
        jdbcTemplate.update("DELETE FROM flipbook_artifact");
        jdbcTemplate.update("DELETE FROM infinite_canvas_artifact");
        jdbcTemplate.update("DELETE FROM phone_artifact");
        jdbcTemplate.update("DELETE FROM gallery");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM file_upload");
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
    void createInfiniteCanvasUsesRegisteredNicknameAndColorOnly() throws Exception {
        UUID userUuid = createExistingUserWithNickname("다현");

        MvcResult result = mockMvc
            .perform(post("/api/v1/infinite-canvas/canvases").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType("application/json").content("""
                    {
                      "color": "#72DDF7"
                    }
                    """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("무한 캔버스 방 생성 성공"))
            .andExpect(jsonPath("$.data.inviteCode").value(INVITE_CODE))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.ownerUserUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.participantCount").value(1))
            .andExpect(jsonPath("$.data.participants[0].userUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.participants[0].nickname").value("다현"))
            .andExpect(jsonPath("$.data.participants[0].color").value("#72DDF7"))
            .andExpect(jsonPath("$.data.participants[0].avatarUrl").isEmpty())
            .andExpect(jsonPath("$.data.participants[0].connected").value(false))
            .andExpect(jsonPath("$.data.participants.length()").value(1))
            .andExpect(jsonPath("$.data.maxParticipants").value(6))
            .andExpect(jsonPath("$.data.elements").doesNotExist())
            .andExpect(jsonPath("$.data.operations").doesNotExist()).andExpect(jsonPath("$.data.locks").doesNotExist())
            .andExpect(jsonPath("$.data.viewport").doesNotExist()).andExpect(jsonPath("$.data.revision").doesNotExist())
            .andReturn();

        JsonNode responseData = readData(result);
        String canvasId = responseData.path("canvasId").asText();
        JsonNode storedCanvas = readStoredJson(canvasKey(canvasId));
        JsonNode storedInvite = readStoredJson("invite:" + INVITE_CODE);

        assertThat(storedCanvas.path("canvasId").asText()).isEqualTo(canvasId);
        assertThat(storedCanvas.path("inviteCode").asText()).isEqualTo(INVITE_CODE);
        assertThat(storedCanvas.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(storedCanvas.path("participants")).hasSize(1);
        assertThat(storedCanvas.path("participants").get(0).path("nickname").asText()).isEqualTo("다현");
        assertThat(storedCanvas.path("participants").get(0).path("color").asText()).isEqualTo("#72DDF7");
        assertThat(storedCanvas.path("participants").get(0).path("avatarUrl").isNull()).isTrue();
        assertThat(storedCanvas.path("participants").get(0).path("connected").asBoolean()).isFalse();
        assertThat(storedCanvas.path("viewport").isNull()).isTrue();
        assertThat(storedInvite.path("inviteCode").asText()).isEqualTo(INVITE_CODE);
        assertThat(storedInvite.path("boothType").asText()).isEqualTo("infinite_canvas");
        assertThat(storedInvite.path("roomId").asText()).isEqualTo(canvasId);
        assertThat(storedInvite.path("roomName").asText()).isEqualTo("다현의 무한 캔버스");
        assertThat(storedInvite.path("expiresAt").asText()).isNotBlank();
    }

    @Test
    void createInfiniteCanvasRejectsDefaultNicknameAndDoesNotStoreCanvas() throws Exception {
        UUID userUuid = createExistingUserWithDefaultNickname();

        mockMvc
            .perform(post("/api/v1/infinite-canvas/canvases").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString())
                .contentType("application/json").content("""
                    {
                      "color": "#72DDF7"
                    }
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("닉네임을 먼저 설정해주세요."));

        assertThat(redisValues).isEmpty();
    }

    @Test
    void createInfiniteCanvasRejectsBlankNicknameAndDoesNotStoreCanvas() throws Exception {
        UUID userUuid = createExistingUserWithNickname("   ");

        mockMvc
            .perform(post("/api/v1/infinite-canvas/canvases").header(ANONYMOUS_USER_UUID_HEADER, userUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("닉네임을 먼저 설정해주세요."));

        assertThat(redisValues).isEmpty();
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
    void saveInfiniteCanvasOutputCreatesArtifactGalleryAndImageUrls() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));
        String imageObjectKey = "uploads/infinite-canvas/output/original.png";
        String thumbnailObjectKey = "uploads/infinite-canvas/output/thumbnail.png";
        UUID imageFileId = insertFileUpload(ownerUuid, "INFINITE_CANVAS", "UPLOADED", imageObjectKey, null);
        UUID thumbnailFileId = insertFileUpload(ownerUuid, "INFINITE_CANVAS", "UPLOADED", thumbnailObjectKey, null);

        MvcResult result = mockMvc
            .perform(post("/api/v1/infinite-canvas/canvases/{canvasId}/outputs", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, ownerUuid.toString()).contentType("application/json").content("""
                    {
                      "imageFileId": "%s",
                      "thumbnailFileId": "%s",
                      "meta": {
                        "tool": "brush",
                        "elementCount": 12
                      }
                    }
                    """.formatted(imageFileId, thumbnailFileId)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("무한 캔버스 출력 이미지 저장 성공"))
            .andExpect(jsonPath("$.data.kind").value("infinite_canvas"))
            .andExpect(jsonPath("$.data.canvasId").value(state.canvasId()))
            .andExpect(jsonPath("$.data.thumbnailUrl").value(publicUrl(thumbnailObjectKey)))
            .andExpect(jsonPath("$.data.contentUrl").value(publicUrl(imageObjectKey)))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty()).andReturn();

        JsonNode responseData = readData(result);
        UUID galleryId = UUID.fromString(responseData.path("galleryId").asText());
        UUID artifactId = UUID.fromString(responseData.path("artifactId").asText());

        assertThat(readString("SELECT CAST(kind AS VARCHAR) FROM artifact WHERE id = ?", artifactId))
            .isEqualTo("infinite_canvas");
        assertThat(readString("SELECT source_room_id FROM artifact WHERE id = ?", artifactId))
            .isEqualTo(state.canvasId());
        assertThat(readString("SELECT thumbnail_url FROM artifact WHERE id = ?", artifactId))
            .isEqualTo(thumbnailObjectKey);
        assertThat(objectMapper.readTree(readString("SELECT meta FROM artifact WHERE id = ?", artifactId)).path("tool")
            .asText()).isEqualTo("brush");
        assertThat(
            readString("SELECT canvas_image_url FROM infinite_canvas_artifact WHERE artifact_id = ?", artifactId))
            .isEqualTo(imageObjectKey);
        assertThat(readString("SELECT CAST(artifact_id AS VARCHAR) FROM gallery WHERE id = ?", galleryId))
            .isEqualTo(artifactId.toString());
        assertThat(readString("SELECT CAST(user_id AS VARCHAR) FROM gallery WHERE id = ?", galleryId))
            .isEqualTo(ownerUuid.toString());

        mockMvc
            .perform(get("/api/v1/artifacts/{artifactId}/image-urls", artifactId).header(ANONYMOUS_USER_UUID_HEADER,
                ownerUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.artifactId").value(artifactId.toString()))
            .andExpect(jsonPath("$.data.kind").value("infinite_canvas"))
            .andExpect(jsonPath("$.data.thumbnailUrl").value(publicUrl(thumbnailObjectKey)))
            .andExpect(jsonPath("$.data.contents.length()").value(1))
            .andExpect(jsonPath("$.data.contents[0].type").value("canvas_image"))
            .andExpect(jsonPath("$.data.contents[0].url").value(publicUrl(imageObjectKey)));
    }

    @Test
    void saveInfiniteCanvasOutputRejectsWrongPurposeFile() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));
        UUID imageFileId = insertFileUpload(ownerUuid, "COMMUNITY", "UPLOADED",
            "uploads/community/not-infinite-canvas.png", null);

        mockMvc
            .perform(post("/api/v1/infinite-canvas/canvases/{canvasId}/outputs", state.canvasId())
                .header(ANONYMOUS_USER_UUID_HEADER, ownerUuid.toString()).contentType("application/json").content("""
                    {
                      "imageFileId": "%s"
                    }
                    """.formatted(imageFileId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("무한 캔버스 출력 파일만 저장할 수 있습니다."));
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
    void joinInviteCodeRejectsDefaultNicknameAndDoesNotStoreParticipant() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        UUID viewerUuid = createExistingUserWithDefaultNickname();
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));
        redisValues.put("invite:" + INVITE_CODE, objectMapper.writeValueAsString(new InviteMetadata(INVITE_CODE,
            "infinite_canvas", state.canvasId(), "Owner의 무한 캔버스", LocalDateTime.now().plusHours(1))));

        mockMvc
            .perform(post("/api/v1/invites/{inviteCode}", INVITE_CODE).header(ANONYMOUS_USER_UUID_HEADER,
                viewerUuid.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("닉네임을 먼저 설정해주세요."));

        JsonNode storedParticipants = readStoredJson(canvasKey(state.canvasId())).path("participants");
        assertThat(storedParticipants).hasSize(1);
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

    @Test
    void updateInfiniteCanvasCursorStoresCursorWithoutRevisionChange() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        InfiniteCanvasCursorResponse response = infiniteCanvasService.updateCursor(ownerUuid.toString(),
            state.canvasId(),
            new InfiniteCanvasCursorRequest(15.0, -30.0, 1.5, objectMapper.createObjectNode().put("tool", "brush")));

        JsonNode storedCanvas = readStoredJson(canvasKey(state.canvasId()));
        assertThat(response.canvasId()).isEqualTo(state.canvasId());
        assertThat(response.cursor().userUuid()).isEqualTo(ownerUuid.toString());
        assertThat(storedCanvas.path("revision").asLong()).isZero();
        assertThat(storedCanvas.path("cursors").path(ownerUuid.toString()).path("x").asDouble()).isEqualTo(15.0);
        assertThat(storedCanvas.path("cursors").path(ownerUuid.toString()).path("payload").path("tool").asText())
            .isEqualTo("brush");
    }

    @Test
    void acquireAndReleaseInfiniteCanvasLockStoresLockState() throws Exception {
        UUID ownerUuid = createExistingUserWithNickname("Owner");
        InfiniteCanvasState state = activeCanvasState(ownerUuid, 6);
        redisValues.put(canvasKey(state.canvasId()), serialize(state));

        InfiniteCanvasLockResponse acquired = infiniteCanvasService.acquireLock(ownerUuid.toString(), state.canvasId(),
            new InfiniteCanvasLockRequest("shape-1"));

        JsonNode lockedCanvas = readStoredJson(canvasKey(state.canvasId()));
        assertThat(acquired.lock()).isNotNull();
        assertThat(acquired.lock().userUuid()).isEqualTo(ownerUuid.toString());
        assertThat(lockedCanvas.path("locks").path("shape-1").path("userUuid").asText())
            .isEqualTo(ownerUuid.toString());

        InfiniteCanvasLockResponse released = infiniteCanvasService.releaseLock(ownerUuid.toString(), state.canvasId(),
            new InfiniteCanvasLockRequest("shape-1"));

        JsonNode releasedCanvas = readStoredJson(canvasKey(state.canvasId()));
        assertThat(released.lock()).isNull();
        assertThat(releasedCanvas.path("locks").has("shape-1")).isFalse();
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

    private void prepareOutputTables() {
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
            CREATE TABLE IF NOT EXISTS fortune_artifact (
                artifact_id UUID PRIMARY KEY,
                description VARCHAR(1000) NOT NULL DEFAULT '{}',
                fortune_image_url VARCHAR(200) NULL,
                user_id UUID,
                fortune_date DATE
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS relay_drawing_artifact (
                artifact_id UUID PRIMARY KEY,
                combined_preview_url VARCHAR(200) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS flipbook_artifact (
                artifact_id UUID PRIMARY KEY,
                gif_url VARCHAR(200) NULL,
                first_image VARCHAR(200) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS infinite_canvas_artifact (
                artifact_id UUID PRIMARY KEY,
                canvas_image_url VARCHAR(200) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS phone_artifact (
                artifact_id UUID PRIMARY KEY,
                phone_image_url VARCHAR(200) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS community_memo (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                artifact_id UUID NULL,
                body_image_url VARCHAR(1000) NULL,
                thumbnail_image_url VARCHAR(1000) NULL,
                deleted_at TIMESTAMP NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS file_upload (
                id UUID PRIMARY KEY,
                user_id UUID NOT NULL,
                purpose VARCHAR(32) NOT NULL,
                original_file_name VARCHAR(255) NOT NULL,
                content_type VARCHAR(100) NOT NULL,
                byte_size BIGINT NOT NULL,
                object_key VARCHAR(500) NOT NULL,
                status VARCHAR(32) NOT NULL,
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                deleted_at TIMESTAMP NULL
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

    private UUID createExistingUserWithDefaultNickname() {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        return userUuid;
    }

    private UUID insertFileUpload(UUID userUuid, String purpose, String status, String objectKey,
        LocalDateTime deletedAt) {
        UUID fileId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("""
            INSERT INTO file_upload (
                id,
                user_id,
                purpose,
                original_file_name,
                content_type,
                byte_size,
                object_key,
                status,
                expires_at,
                created_at,
                updated_at,
                deleted_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, fileId, userUuid, purpose, "canvas.png", "image/png", 1024L, objectKey, status,
            Timestamp.valueOf(now.plusHours(1)), Timestamp.valueOf(now), Timestamp.valueOf(now),
            deletedAt == null ? null : Timestamp.valueOf(deletedAt));

        return fileId;
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

    private String readString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private String publicUrl(String objectKey) {
        return MINIO_PUBLIC_URL + objectKey;
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
