package com.nemonicworld.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.support.IntegrationTest;
import com.nemonicworld.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
/**
 * 익명 사용자 등록 중 내부 예외가 발생했을 때 전역 예외 처리 흐름을 검증합니다.
 */
class UserControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    /**
     * 내부 예외 메시지나 민감한 내용이 API 응답에 노출되지 않는지 검증합니다.
     */
    @Test
    void createAnonymousUserDoesNotExposeInternalError() throws Exception {
        given(userService.createAnonymousUser(any())).willThrow(new IllegalStateException("database password=secret"));

        mockMvc.perform(post("/users/anonymous")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
            .andExpect(content().string(not(containsString("database password=secret"))));
    }
}
