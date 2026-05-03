package com.nemonicworld.user.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.user.dto.request.AnonymousUserBirthInfoRequest;
import com.nemonicworld.user.dto.request.AnonymousUserNicknameRequest;
import com.nemonicworld.user.dto.response.AnonymousUserBirthInfoResponse;
import com.nemonicworld.user.dto.response.AnonymousUserNicknameResponse;
import com.nemonicworld.user.dto.response.AnonymousUserProfileResponse;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.dto.response.AnonymousUserVerifyResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "User", description = "사용자 API")
/**
 * 익명 사용자와 관련된 HTTP 요청을 받는 컨트롤러입니다.
 *
 * <p>
 * 컨트롤러는 요청/응답 변환만 담당하고, UUID 발급과 저장 정책은 서비스 계층에 위임합니다.
 */
public class UserController {

    private static final String ANONYMOUS_USER_CREATED_MESSAGE = "익명 사용자 UUID 발급 성공";
    private static final String ANONYMOUS_USER_VERIFIED_MESSAGE = "익명 사용자 UUID 확인 성공";
    private static final String ANONYMOUS_USER_NICKNAME_UPDATED_MESSAGE = "닉네임 설정/수정 성공";
    private static final String ANONYMOUS_USER_BIRTH_INFO_CREATED_MESSAGE = "생년월일 정보 등록 성공";
    private static final String ANONYMOUS_USER_BIRTH_INFO_UPDATED_MESSAGE = "생년월일 정보 수정 성공";
    private static final String ANONYMOUS_USER_PROFILE_FOUND_MESSAGE = "내 프로필 조회 성공";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 앱 첫 진입 시 사용할 익명 사용자 UUID를 서버에서 새로 발급합니다.
     *
     * <p>
     * 요청 body는 받지 않으며, User-Agent 헤더는 선택값으로만 전달합니다.
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

    /**
     * 클라이언트가 보관 중인 익명 사용자 UUID가 서버에 존재하는지 확인하고 재방문 시각을 갱신합니다.
     */
    @PostMapping("/anonymous/verify")
    @Operation(summary = "익명 사용자 UUID 확인", description = "클라이언트가 보관 중인 익명 사용자 UUID를 검증하고 재방문 시각을 갱신합니다.")
    @Parameters({
        @Parameter(name = AnonymousUserHeaders.ANONYMOUS_USER_UUID, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID"),
        @Parameter(name = HttpHeaders.USER_AGENT, in = ParameterIn.HEADER, description = "없거나 공백이면 unknown으로 저장됩니다.")})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "익명 사용자 UUID 확인 성공")
    public ResponseEntity<ApiResponse<AnonymousUserVerifyResponse>> verifyAnonymousUser(
        @RequestHeader(value = AnonymousUserHeaders.ANONYMOUS_USER_UUID, required = false) String userUuid,
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        AnonymousUserVerifyResponse response = userService.verifyAnonymousUser(userUuid, userAgent);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_VERIFIED_MESSAGE, response));
    }

    /**
     * 서버에 등록된 익명 사용자의 닉네임을 설정하거나 수정합니다.
     */
    @PatchMapping("/anonymous/nickname")
    @Operation(summary = "익명 사용자 닉네임 설정/수정", description = "서버에 등록된 익명 사용자의 닉네임을 설정하거나 수정합니다.")
    @Parameter(name = AnonymousUserHeaders.ANONYMOUS_USER_UUID, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "닉네임 설정/수정 성공")
    public ResponseEntity<ApiResponse<AnonymousUserNicknameResponse>> updateAnonymousUserNickname(
        @RequestHeader(value = AnonymousUserHeaders.ANONYMOUS_USER_UUID, required = false) String userUuid,
        @RequestBody AnonymousUserNicknameRequest request) {
        AnonymousUserNicknameResponse response = userService.updateAnonymousUserNickname(userUuid, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_NICKNAME_UPDATED_MESSAGE, response));
    }

    /**
     * 운세 기능에서 재사용할 생년월일 정보를 최초 등록합니다.
     */
    @PostMapping("/anonymous/birth-info")
    @Operation(summary = "익명 사용자 생년월일 정보 등록", description = "운세 기능 최초 진입 시 필요한 생년월일, 생시, 양력/음력 여부를 등록합니다.")
    @Parameter(name = AnonymousUserHeaders.ANONYMOUS_USER_UUID, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생년월일 정보 등록 성공")
    public ResponseEntity<ApiResponse<AnonymousUserBirthInfoResponse>> registerAnonymousUserBirthInfo(
        @RequestHeader(value = AnonymousUserHeaders.ANONYMOUS_USER_UUID, required = false) String userUuid,
        @RequestBody AnonymousUserBirthInfoRequest request) {
        AnonymousUserBirthInfoResponse response = userService.registerAnonymousUserBirthInfo(userUuid, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_BIRTH_INFO_CREATED_MESSAGE, response));
    }

    /**
     * 운세 기능에서 재사용할 생년월일 정보를 수정합니다.
     */
    @PatchMapping("/anonymous/birth-info")
    @Operation(summary = "익명 사용자 생년월일 정보 수정", description = "이미 등록된 생년월일, 생시, 양력/음력 여부를 수정합니다.")
    @Parameter(name = AnonymousUserHeaders.ANONYMOUS_USER_UUID, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생년월일 정보 수정 성공")
    public ResponseEntity<ApiResponse<AnonymousUserBirthInfoResponse>> updateAnonymousUserBirthInfo(
        @RequestHeader(value = AnonymousUserHeaders.ANONYMOUS_USER_UUID, required = false) String userUuid,
        @RequestBody AnonymousUserBirthInfoRequest request) {
        AnonymousUserBirthInfoResponse response = userService.updateAnonymousUserBirthInfo(userUuid, request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_BIRTH_INFO_UPDATED_MESSAGE, response));
    }

    /**
     * 서버에 등록된 익명 사용자의 프로필 정보를 조회합니다.
     */
    @GetMapping("/anonymous/profile")
    @Operation(summary = "익명 사용자 프로필 조회", description = "서버에 등록된 익명 사용자의 재사용 가능 프로필 정보를 조회합니다.")
    @Parameter(name = AnonymousUserHeaders.ANONYMOUS_USER_UUID, in = ParameterIn.HEADER, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 프로필 조회 성공")
    public ResponseEntity<ApiResponse<AnonymousUserProfileResponse>> getAnonymousUserProfile(
        @RequestHeader(value = AnonymousUserHeaders.ANONYMOUS_USER_UUID, required = false) String userUuid) {
        AnonymousUserProfileResponse response = userService.getAnonymousUserProfile(userUuid);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_PROFILE_FOUND_MESSAGE, response));
    }
}
