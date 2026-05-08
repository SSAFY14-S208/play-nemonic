package com.nemonicworld.fortune.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.dto.response.FortuneResponse;
import com.nemonicworld.fortune.service.FortuneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fortune")
@Tag(name = "Fortune", description = "오늘의 운세 API")
/**
 * 오늘의 운세 부스에서 사용하는 공개 API 요청을 처리하는 컨트롤러입니다.
 */
public class FortuneController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String FORTUNE_AVAILABILITY_FOUND_MESSAGE = "오늘의 운세 생성 가능 여부 조회 성공";
    private static final String FORTUNE_FOUND_MESSAGE = "오늘의 운세 조회 성공";
    private static final String FORTUNE_CREATED_MESSAGE = "오늘의 운세 생성 성공";

    private final FortuneService fortuneService;

    public FortuneController(FortuneService fortuneService) {
        this.fortuneService = fortuneService;
    }

    /**
     * UUID와 KST 날짜 기준으로 오늘 운세를 생성할 수 있는지 확인합니다.
     */
    @GetMapping("/today/availability")
    @Operation(summary = "오늘의 운세 생성 가능 여부 조회", description = "UUID와 KST 날짜 기준으로 오늘의 운세를 생성할 수 있는지 조회합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "오늘의 운세 생성 가능 여부 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FortuneAvailabilityResponse>> getTodayAvailability(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FortuneAvailabilityResponse response = fortuneService.getTodayAvailability(userUuid);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FORTUNE_AVAILABILITY_FOUND_MESSAGE, response));
    }

    /**
     * UUID와 KST 날짜 기준으로 오늘 이미 생성된 운세 결과를 재조회합니다.
     */
    @GetMapping("/today")
    @Operation(summary = "오늘의 운세 재조회", description = "UUID와 KST 날짜 기준으로 오늘 이미 생성된 운세 결과를 조회합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "오늘의 운세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 리소스", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "오늘 운세 없음", value = OpenApiErrorExamples.FORTUNE_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FortuneResponse>> getTodayFortune(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        FortuneResponse response = fortuneService.getTodayFortune(userUuid);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FORTUNE_FOUND_MESSAGE, response));
    }

    /**
     * 프론트에서 계산한 만세력 결과를 기반으로 오늘의 운세를 생성하고 갤러리에 보관합니다.
     */
    @PostMapping
    @Operation(summary = "오늘의 운세 생성", description = "프론트 만세력 계산 결과를 받아 운세 결과와 카드 이미지를 생성하고 갤러리에 저장합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "오늘의 운세 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "invalidUuid", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "invalidSaju", value = OpenApiErrorExamples.INVALID_FORTUNE_SAJU)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "오늘 운세 이미 생성됨", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FORTUNE_ALREADY_CREATED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "GMS 생성 실패", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FORTUNE_GMS_UNAVAILABLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SERVER_ERROR)))})
    public ResponseEntity<ApiResponse<FortuneResponse>> createFortune(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @Valid @RequestBody FortuneCreateRequest request) {
        FortuneResponse response = fortuneService.createFortune(userUuid, request);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(FORTUNE_CREATED_MESSAGE, response));
    }
}
