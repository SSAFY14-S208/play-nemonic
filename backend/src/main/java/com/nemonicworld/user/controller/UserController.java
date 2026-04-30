package com.nemonicworld.user.controller;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final String ANONYMOUS_USER_CREATED_MESSAGE = "익명 사용자 UUID 발급 성공";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/anonymous")
    public ResponseEntity<ApiResponse<AnonymousUserResponse>> createAnonymousUser(
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        AnonymousUserResponse response = userService.createAnonymousUser(userAgent);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(ANONYMOUS_USER_CREATED_MESSAGE, response));
    }
}
