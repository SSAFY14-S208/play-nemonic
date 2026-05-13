package com.nemonicworld.user.controller;

import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = OpenApiTags.USER, description = OpenApiTags.USER_DESCRIPTION)
/**
 * 익명 사용자와 관련된 HTTP 요청을 받는 컨트롤러입니다.
 *
 * <p>
 * 컨트롤러는 요청/응답 변환만 담당하고, UUID 발급과 저장 정책은 서비스 계층에 위임합니다.
 */
public class UserController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String USER_AGENT_HEADER = HttpHeaders.USER_AGENT;
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
    @Parameters({@Parameter(name = USER_AGENT_HEADER, in = ParameterIn.HEADER, description = "User-Agent")})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "익명 사용자 UUID 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = OpenApiCommonResponses.SERVER_ERROR_REF)})
    public ResponseEntity<ApiResponse<AnonymousUserResponse>> createAnonymousUser(
        @RequestHeader(value = USER_AGENT_HEADER, required = false) String userAgent) {
        AnonymousUserResponse response = userService.createAnonymousUser(userAgent);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_CREATED_MESSAGE, response));
    }

    /**
     * 클라이언트가 보관 중인 익명 사용자 UUID가 서버에 존재하는지 확인하고 재방문 시각을 갱신합니다.
     */
    @PostMapping("/anonymous/verify")
    @Operation(summary = "익명 사용자 UUID 확인", description = "클라이언트가 보관 중인 익명 사용자 UUID를 검증하고 재방문 시각을 갱신합니다.")
    @Parameters({@Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true),
        @Parameter(name = USER_AGENT_HEADER, in = ParameterIn.HEADER, description = "User-Agent")})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "익명 사용자 UUID 확인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AnonymousUserVerifyResponse>> verifyAnonymousUser(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestHeader(value = USER_AGENT_HEADER, required = false) String userAgent) {
        AnonymousUserVerifyResponse response = userService.verifyAnonymousUser(userUuid, userAgent);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_VERIFIED_MESSAGE, response));
    }

    /**
     * 서버에 등록된 익명 사용자의 닉네임을 설정하거나 수정합니다.
     */
    @PatchMapping("/anonymous/nickname")
    @Operation(summary = "익명 사용자 닉네임 설정/수정", description = "서버에 등록된 익명 사용자의 닉네임을 설정하거나 수정합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "닉네임 설정/수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "닉네임 오류", value = OpenApiErrorExamples.INVALID_NICKNAME)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AnonymousUserNicknameResponse>> updateAnonymousUserNickname(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
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
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생년월일 정보 등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "생년월일 정보 형식 오류", value = OpenApiErrorExamples.INVALID_BIRTH_INFO)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 생년월일 정보", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.BIRTH_INFO_ALREADY_REGISTERED)))})
    public ResponseEntity<ApiResponse<AnonymousUserBirthInfoResponse>> registerAnonymousUserBirthInfo(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
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
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생년월일 정보 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "생년월일 정보 형식 오류", value = OpenApiErrorExamples.INVALID_BIRTH_INFO)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 등록된 생년월일 정보", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "생년월일 정보 없음", value = OpenApiErrorExamples.BIRTH_INFO_NOT_REGISTERED)}))})
    public ResponseEntity<ApiResponse<AnonymousUserBirthInfoResponse>> updateAnonymousUserBirthInfo(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
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
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 프로필 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<AnonymousUserProfileResponse>> getAnonymousUserProfile(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        AnonymousUserProfileResponse response = userService.getAnonymousUserProfile(userUuid);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(ANONYMOUS_USER_PROFILE_FOUND_MESSAGE, response));
    }
}
