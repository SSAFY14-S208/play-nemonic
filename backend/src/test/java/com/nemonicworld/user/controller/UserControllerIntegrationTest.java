package com.nemonicworld.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.domain.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
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

    // 공통 ApiResponse에서 data 노드만 꺼내 테스트 가독성을 높입니다.
    private JsonNode readData(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();

        return objectMapper.readTree(content).path("data");
    }
}
