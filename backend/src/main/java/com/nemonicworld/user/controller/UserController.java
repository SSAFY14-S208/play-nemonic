package com.nemonicworld.user.controller;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "User", description = "사용자 API")
/**
 * 익명 사용자와 관련된 HTTP 요청을 받는 컨트롤러입니다.
 *
 * <p>컨트롤러는 요청/응답 변환만 담당하고, UUID 발급과 저장 정책은 서비스 계층에 위임합니다.
 */
public class UserController {

    private static final String ANONYMOUS_USER_CREATED_MESSAGE = "익명 사용자 UUID 발급 성공";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 앱 첫 진입 시 사용할 익명 사용자 UUID를 서버에서 새로 발급합니다.
     *
     * <p>요청 body는 받지 않으며, User-Agent 헤더는 선택값으로만 전달합니다.
     */
    @PostMapping("/anonymous")
    @Operation(summary = "익명 사용자 UUID 발급", description = "앱 첫 진입 시 서버가 새 익명 사용자 UUID를 발급하고 등록합니다.")
    @Parameters({
        @Parameter(name = HttpHeaders.USER_AGENT, in = ParameterIn.HEADER, description = "없거나 공백이면 unknown으로 저장됩니다.")})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "익명 사용자 UUID 발급 성공")
    public ResponseEntity<ApiResponse<AnonymousUserResponse>> createAnonymousUser(
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        AnonymousUserResponse response = userService.createAnonymousUser(userAgent);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_CREATED_MESSAGE, response));
    }
}
