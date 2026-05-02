package com.nemonicworld.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Sql(statements = "DELETE FROM app_user")
/**
 * 익명 사용자 UUID 발급 API의 정상 흐름과 저장 결과를 검증합니다.
 */
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    /**
     * 201 응답과 ApiResponse 형식, 그리고 app_user 저장 필드를 함께 검증합니다.
     */
    @Test
    void createAnonymousUserReturnsCreatedResponseAndPersistsUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/users/anonymous").header(HttpHeaders.USER_AGENT, "MangoApp/1.0"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("익명 사용자 UUID 발급 성공"))
            .andExpect(jsonPath("$.data.nickname").value(AppUser.ANONYMOUS_NICKNAME))
            .andExpect(jsonPath("$.data.createdAt").exists()).andReturn();

        JsonNode data = readData(result);
        UUID userUuid = UUID.fromString(data.path("userUuid").asText());
        AppUser savedUser = userRepository.findById(userUuid).orElseThrow();

        assertThat(savedUser.getNickname()).isEqualTo(AppUser.ANONYMOUS_NICKNAME);
        assertThat(savedUser.getUserAgent()).isEqualTo("MangoApp/1.0");
        assertThat(savedUser.getBirthday()).isNull();
        assertThat(savedUser.getBirthtime()).isNull();
        assertThat(savedUser.getCreatedAt()).isEqualTo(LocalDateTime.parse(data.path("createdAt").asText()));
        assertThat(savedUser.getLastSeenAt()).isEqualTo(savedUser.getCreatedAt());
        assertThat(savedUser.getUpdatedAt()).isEqualTo(savedUser.getCreatedAt());
    }

    /**
     * 같은 API를 여러 번 호출해도 매번 다른 UUID가 발급되는지 검증합니다.
     */
    @Test
    void createAnonymousUserIssuesDifferentUuidEveryCall() throws Exception {
        UUID firstUserUuid = createAnonymousUser("MangoApp/1.0");
        UUID secondUserUuid = createAnonymousUser("MangoApp/1.0");

        assertThat(firstUserUuid).isNotEqualTo(secondUserUuid);
        assertThat(userRepository.existsById(firstUserUuid)).isTrue();
        assertThat(userRepository.existsById(secondUserUuid)).isTrue();
        assertThat(userRepository.count()).isEqualTo(2);
    }

    /**
     * User-Agent가 없거나 공백이어도 unknown으로 저장되어 등록이 성공하는지 검증합니다.
     */
    @Test
    void createAnonymousUserUsesUnknownWhenUserAgentIsMissingOrBlank() throws Exception {
        UUID missingUserAgentUserUuid = createAnonymousUserWithoutUserAgent();
        UUID blankUserAgentUserUuid = createAnonymousUser(" ");

        assertThat(userRepository.findById(missingUserAgentUserUuid).orElseThrow().getUserAgent()).isEqualTo("unknown");
        assertThat(userRepository.findById(blankUserAgentUserUuid).orElseThrow().getUserAgent()).isEqualTo("unknown");
    }

    /**
     * 서버에 존재하는 UUID를 검증하면 200 응답과 함께 재방문 시각, 수정 시각, User-Agent가 갱신되는지 검증합니다.
     */
    @Test
    void verifyAnonymousUserReturnsOkResponseAndUpdatesVisitMetadata() throws Exception {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "OldAgent/1.0", createdAt));

        MvcResult result = mockMvc
            .perform(post("/users/anonymous/verify").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "MangoApp/2.0").content(verifyRequestBody(userUuid)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("익명 사용자 UUID 확인 성공"))
            .andExpect(jsonPath("$.data.userUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.nickname").value(AppUser.ANONYMOUS_NICKNAME))
            .andExpect(jsonPath("$.data.lastSeenAt").exists()).andReturn();

        LocalDateTime responseLastSeenAt = LocalDateTime.parse(readData(result).path("lastSeenAt").asText());
        AppUser savedUser = userRepository.findById(userUuid).orElseThrow();

        assertThat(savedUser.getLastSeenAt()).isEqualTo(responseLastSeenAt);
        assertThat(savedUser.getUpdatedAt()).isEqualTo(responseLastSeenAt);
        assertThat(savedUser.getLastSeenAt()).isAfter(createdAt);
        assertThat(savedUser.getUserAgent()).isEqualTo("MangoApp/2.0");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * 검증 API에서도 User-Agent가 없거나 공백이면 unknown으로 갱신되는지 확인합니다.
     */
    @Test
    void verifyAnonymousUserUsesUnknownWhenUserAgentIsMissingOrBlank() throws Exception {
        UUID missingUserAgentUserUuid = createExistingUser("InitialAgent/1.0");
        UUID blankUserAgentUserUuid = createExistingUser("InitialAgent/1.0");

        verifyAnonymousUserWithoutUserAgent(missingUserAgentUserUuid);
        verifyAnonymousUser(blankUserAgentUserUuid, " ");

        assertThat(userRepository.findById(missingUserAgentUserUuid).orElseThrow().getUserAgent()).isEqualTo("unknown");
        assertThat(userRepository.findById(blankUserAgentUserUuid).orElseThrow().getUserAgent()).isEqualTo("unknown");
        assertThat(userRepository.count()).isEqualTo(2);
    }

    /**
     * UUID 형식이 잘못된 경우 400 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void verifyAnonymousUserRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(post("/users/anonymous/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userUuid\":\"not-a-uuid\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
    }

    /**
     * UUID 형식은 맞지만 서버에 없는 경우 404 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void verifyAnonymousUserReturnsNotFoundAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(post("/users/anonymous/verify").contentType(MediaType.APPLICATION_JSON)
                .content(verifyRequestBody(missingUserUuid)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    /**
     * 서버에 존재하는 UUID로 닉네임을 설정하면 200 응답과 함께 nickname, updated_at이 갱신되는지 검증합니다.
     */
    @Test
    void updateAnonymousUserNicknameReturnsOkResponseAndUpdatesNickname() throws Exception {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt));

        MvcResult result = mockMvc
            .perform(patch("/users/anonymous/nickname").contentType(MediaType.APPLICATION_JSON)
                .content(nicknameRequestBody(userUuid, "망고")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("닉네임 설정/수정 성공"))
            .andExpect(jsonPath("$.data.userUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.nickname").value("망고")).andExpect(jsonPath("$.data.updatedAt").exists())
            .andReturn();

        LocalDateTime responseUpdatedAt = LocalDateTime.parse(readData(result).path("updatedAt").asText());
        AppUser savedUser = userRepository.findById(userUuid).orElseThrow();

        assertThat(savedUser.getNickname()).isEqualTo("망고");
        assertThat(savedUser.getUpdatedAt()).isEqualTo(responseUpdatedAt);
        assertThat(savedUser.getUpdatedAt()).isAfter(createdAt);
        assertThat(savedUser.getLastSeenAt()).isEqualTo(createdAt);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * 같은 엔드포인트로 최초 설정 이후 닉네임을 다시 변경할 수 있는지 검증합니다.
     */
    @Test
    void updateAnonymousUserNicknameCanChangeExistingNickname() throws Exception {
        UUID userUuid = createExistingUser("MangoApp/1.0");

        updateAnonymousUserNickname(userUuid, "망고");
        updateAnonymousUserNickname(userUuid, "다시망고");

        assertThat(userRepository.findById(userUuid).orElseThrow().getNickname()).isEqualTo("다시망고");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * 공백이 포함된 닉네임, 특수문자, 이모지는 길이 조건만 만족하면 저장되는지 검증합니다.
     */
    @Test
    void updateAnonymousUserNicknameAllowsSpacesSymbolsAndEmoji() throws Exception {
        UUID userUuid = createExistingUser("MangoApp/1.0");
        String nickname = " 망고 팀🙂! ";

        mockMvc
            .perform(patch("/users/anonymous/nickname").contentType(MediaType.APPLICATION_JSON)
                .content(nicknameRequestBody(userUuid, nickname)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value(nickname));

        assertThat(userRepository.findById(userUuid).orElseThrow().getNickname()).isEqualTo(nickname);
    }

    /**
     * UUID 형식이 잘못된 경우 400 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void updateAnonymousUserNicknameRejectsInvalidUuidFormatAndDoesNotCreateUser() throws Exception {
        mockMvc
            .perform(patch("/users/anonymous/nickname").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userUuid\":\"not-a-uuid\",\"nickname\":\"망고\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
    }

    /**
     * UUID 형식은 맞지만 서버에 없는 경우 404 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void updateAnonymousUserNicknameReturnsNotFoundAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc
            .perform(patch("/users/anonymous/nickname").contentType(MediaType.APPLICATION_JSON)
                .content(nicknameRequestBody(missingUserUuid, "망고")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    /**
     * 닉네임이 없거나 전체 공백이거나 10자를 초과하면 400 응답을 반환하고 기존 값을 바꾸지 않는지 검증합니다.
     */
    @Test
    void updateAnonymousUserNicknameRejectsInvalidNicknameAndDoesNotModifyUser() throws Exception {
        UUID missingNicknameUserUuid = createExistingUser("MangoApp/1.0");
        UUID blankNicknameUserUuid = createExistingUser("MangoApp/1.0");
        UUID tooLongNicknameUserUuid = createExistingUser("MangoApp/1.0");

        assertInvalidNickname(missingNicknameRequestBody(missingNicknameUserUuid));
        assertInvalidNickname(nicknameRequestBody(blankNicknameUserUuid, "   "));
        assertInvalidNickname(nicknameRequestBody(tooLongNicknameUserUuid, "12345678901"));

        assertThat(userRepository.findById(missingNicknameUserUuid).orElseThrow().getNickname())
            .isEqualTo(AppUser.ANONYMOUS_NICKNAME);
        assertThat(userRepository.findById(blankNicknameUserUuid).orElseThrow().getNickname())
            .isEqualTo(AppUser.ANONYMOUS_NICKNAME);
        assertThat(userRepository.findById(tooLongNicknameUserUuid).orElseThrow().getNickname())
            .isEqualTo(AppUser.ANONYMOUS_NICKNAME);
        assertThat(userRepository.count()).isEqualTo(3);
    }

    /**
     * 서버에 존재하는 UUID로 프로필을 조회하면 재사용 가능한 사용자 정보가 반환되고 DB 메타데이터는 변경되지 않는지 검증합니다.
     */
    @Test
    void getAnonymousUserProfileReturnsOkResponseAndDoesNotUpdateMetadata() throws Exception {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime updatedAt = createdAt.plusHours(1);
        LocalDate birthday = LocalDate.of(1998, 3, 15);
        LocalTime birthtime = LocalTime.of(13, 30);
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", createdAt);
        appUser.updateNickname("망고", updatedAt);
        setBirthInfo(appUser, birthday, birthtime);
        userRepository.saveAndFlush(appUser);

        mockMvc.perform(get("/users/anonymous/profile").param("userUuid", userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("내 프로필 조회 성공"))
            .andExpect(jsonPath("$.data.userUuid").value(userUuid.toString()))
            .andExpect(jsonPath("$.data.nickname").value("망고"))
            .andExpect(jsonPath("$.data.birthday").value("1998-03-15"))
            .andExpect(jsonPath("$.data.birthtime").value("13:30:00"))
            .andExpect(jsonPath("$.data.createdAt").value(createdAt.toString()))
            .andExpect(jsonPath("$.data.updatedAt").value(updatedAt.toString()))
            .andExpect(jsonPath("$.data.lastSeenAt").value(createdAt.toString()))
            .andExpect(jsonPath("$.data.userAgent").doesNotExist());

        AppUser savedUser = userRepository.findById(userUuid).orElseThrow();
        assertThat(savedUser.getLastSeenAt()).isEqualTo(createdAt);
        assertThat(savedUser.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(savedUser.getUserAgent()).isEqualTo("MangoApp/1.0");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * 생년월일과 생시가 아직 등록되지 않은 사용자는 프로필 응답에서 null로 반환되는지 검증합니다.
     */
    @Test
    void getAnonymousUserProfileReturnsNullBirthInfoWhenMissing() throws Exception {
        UUID userUuid = createExistingUser("MangoApp/1.0");

        mockMvc.perform(get("/users/anonymous/profile").param("userUuid", userUuid.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.birthday").value(nullValue()))
            .andExpect(jsonPath("$.data.birthtime").value(nullValue()));

        assertThat(userRepository.findById(userUuid).orElseThrow().getBirthday()).isNull();
        assertThat(userRepository.findById(userUuid).orElseThrow().getBirthtime()).isNull();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    /**
     * UUID가 없거나 형식이 잘못된 경우 400 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void getAnonymousUserProfileRejectsMissingOrInvalidUuidAndDoesNotCreateUser() throws Exception {
        mockMvc.perform(get("/users/anonymous/profile")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        mockMvc.perform(get("/users/anonymous/profile").param("userUuid", "not-a-uuid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("유효하지 않은 UUID 형식입니다."));

        assertThat(userRepository.count()).isZero();
    }

    /**
     * UUID 형식은 맞지만 서버에 없는 경우 404 응답을 반환하고 새 사용자를 만들지 않는지 검증합니다.
     */
    @Test
    void getAnonymousUserProfileReturnsNotFoundAndDoesNotCreateUser() throws Exception {
        UUID missingUserUuid = UUID.randomUUID();

        mockMvc.perform(get("/users/anonymous/profile").param("userUuid", missingUserUuid.toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."));

        assertThat(userRepository.existsById(missingUserUuid)).isFalse();
        assertThat(userRepository.count()).isZero();
    }

    // 테스트에서 반복되는 정상 호출 흐름을 감싼 헬퍼입니다.
    private UUID createAnonymousUser(String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/anonymous").header(HttpHeaders.USER_AGENT, userAgent))
            .andExpect(status().isCreated()).andReturn();

        return UUID.fromString(readData(result).path("userUuid").asText());
    }

    // User-Agent 헤더를 아예 보내지 않는 케이스를 만들기 위한 헬퍼입니다.
    private UUID createAnonymousUserWithoutUserAgent() throws Exception {
        MvcResult result = mockMvc.perform(post("/users/anonymous")).andExpect(status().isCreated()).andReturn();

        return UUID.fromString(readData(result).path("userUuid").asText());
    }

    // 검증 API에서 반복되는 정상 호출 흐름을 감싼 헬퍼입니다.
    private void verifyAnonymousUser(UUID userUuid, String userAgent) throws Exception {
        mockMvc
            .perform(post("/users/anonymous/verify").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, userAgent).content(verifyRequestBody(userUuid)))
            .andExpect(status().isOk());
    }

    // User-Agent 헤더를 아예 보내지 않는 검증 API 케이스를 만들기 위한 헬퍼입니다.
    private void verifyAnonymousUserWithoutUserAgent(UUID userUuid) throws Exception {
        mockMvc.perform(post("/users/anonymous/verify").contentType(MediaType.APPLICATION_JSON)
            .content(verifyRequestBody(userUuid))).andExpect(status().isOk());
    }

    // 닉네임 설정/수정 API에서 반복되는 정상 호출 흐름을 감싼 헬퍼입니다.
    private void updateAnonymousUserNickname(UUID userUuid, String nickname) throws Exception {
        mockMvc.perform(patch("/users/anonymous/nickname").contentType(MediaType.APPLICATION_JSON)
            .content(nicknameRequestBody(userUuid, nickname))).andExpect(status().isOk());
    }

    // 닉네임 validation 실패 응답의 공통 계약을 확인합니다.
    private void assertInvalidNickname(String requestBody) throws Exception {
        mockMvc.perform(patch("/users/anonymous/nickname").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("닉네임은 1자 이상 10자 이하로 입력해주세요."));
    }

    private UUID createExistingUser(String userAgent) {
        UUID userUuid = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

        userRepository.saveAndFlush(AppUser.createAnonymous(userUuid, userAgent, createdAt));

        return userUuid;
    }

    private void setBirthInfo(AppUser appUser, LocalDate birthday, LocalTime birthtime) {
        ReflectionTestUtils.setField(appUser, "birthday", birthday);
        ReflectionTestUtils.setField(appUser, "birthtime", birthtime);
    }

    private String verifyRequestBody(UUID userUuid) {
        return "{\"userUuid\":\"" + userUuid + "\"}";
    }

    private String nicknameRequestBody(UUID userUuid, String nickname) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("userUuid", userUuid.toString());
        request.put("nickname", nickname);

        return objectMapper.writeValueAsString(request);
    }

    private String missingNicknameRequestBody(UUID userUuid) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("userUuid", userUuid.toString());

        return objectMapper.writeValueAsString(request);
    }

    // 공통 ApiResponse에서 data 노드만 꺼내 테스트 가독성을 높입니다.
    private JsonNode readData(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();

        return objectMapper.readTree(content).path("data");
    }
}
