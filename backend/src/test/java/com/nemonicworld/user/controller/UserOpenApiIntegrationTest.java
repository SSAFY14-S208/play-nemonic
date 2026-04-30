package com.nemonicworld.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemonicworld.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class UserOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUserApiIsExposedInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/users/anonymous'].post.summary").value("익명 사용자 UUID 발급"))
            .andExpect(jsonPath("$.paths['/users/anonymous'].post.tags[0]").value("User")).andExpect(
                jsonPath("$.paths['/users/anonymous'].post.responses['201'].description").value("익명 사용자 UUID 발급 성공"));
    }
}
