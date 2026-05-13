package com.nemonicworld.backoffice.setting.controller;

import com.nemonicworld.common.openapi.OpenApiTags;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.auth.service.AdminClientInfoResolver;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterTypedUpdateRequest;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.service.SystemParameterService;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.common.openapi.OpenApiCommonResponses;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/system-parameters")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
@Tag(name = OpenApiTags.SYSTEM_PARAMETER, description = OpenApiTags.SYSTEM_PARAMETER_DESCRIPTION)
public class SystemParameterController {

    private static final String LIST_SUCCESS_MESSAGE = "시스템 파라미터 목록 조회 성공";
    private static final String BULK_UPDATE_SUCCESS_MESSAGE = "시스템 파라미터 수정 성공";

    private final SystemParameterService systemParameterService;
    private final AdminClientInfoResolver adminClientInfoResolver;

    public SystemParameterController(SystemParameterService systemParameterService,
        AdminClientInfoResolver adminClientInfoResolver) {
        this.systemParameterService = systemParameterService;
        this.adminClientInfoResolver = adminClientInfoResolver;
    }

    @GetMapping
    @Operation(summary = "시스템 파라미터 목록 조회", description = "관리자가 백오피스 시스템 파라미터 목록을 조회합니다.")
    @Parameter(name = "keyword", in = ParameterIn.QUERY, description = "파라미터 키 검색어")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "시스템 파라미터 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF)})
    public ResponseEntity<ApiResponse<SystemParameterListResponse>> getSystemParameters(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @RequestParam(name = "keyword", required = false) String keyword) {
        SystemParameterListResponse response = systemParameterService.getSystemParameters(adminPrincipal, keyword);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(LIST_SUCCESS_MESSAGE, response));
    }

    @PatchMapping
    @Operation(summary = "시스템 파라미터 일괄 수정", description = "관리자가 요청 본문에 포함한 시스템 파라미터 필드만 한 트랜잭션으로 수정합니다. "
        + "여러 필드 중 하나라도 유효하지 않으면 전체 수정은 실패합니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = SystemParameterTypedUpdateRequest.class), examples = @ExampleObject(name = "수정 가능한 시스템 파라미터 변경", value = """
        {
          "communityMaxMemoCount": {
            "value": 50,
            "unit": "count",
            "description": "커뮤니티 캔버스 표시 메모 수 제한"
          },
          "communityReportHideThreshold": {
            "value": 5,
            "unit": "count",
            "description": "커뮤니티 메모 자동 숨김 신고 기준"
          },
          "relayRoomParticipantLimit": {
            "min": 3,
            "max": 8,
            "unit": "people",
            "description": "릴레이 방 참여 인원 제한"
          },
          "relayRoomTimeLimitSeconds": {
            "default": 45,
            "allowed": [30, 45, 60],
            "unit": "seconds",
            "description": "릴레이 방 그리기 제한 시간"
          },
          "relayReconnectGraceSeconds": {
            "value": 10,
            "unit": "seconds",
            "description": "릴레이 진행 중 재연결 유예 시간"
          },
          "flipbookRoomParticipantLimit": {
            "min": 2,
            "max": 6,
            "unit": "people",
            "description": "플립북 방 참여 인원 제한"
          },
          "flipbookRoomTimeLimitSeconds": {
            "default": 45,
            "allowed": [30, 45, 60],
            "unit": "seconds",
            "description": "플립북 방 그리기 제한 시간"
          },
          "flipbookMinFramesPerFlipbook": {
            "value": 8,
            "unit": "frames",
            "description": "완성 플립북 최소 프레임 수"
          },
          "flipbookReconnectGraceSeconds": {
            "value": 10,
            "unit": "seconds",
            "description": "플립북 진행 중 재연결 유예 시간"
          },
          "fortuneDailyLimit": {
            "value": 1,
            "unit": "count",
            "description": "익명 사용자별 일일 운세 생성 제한"
          },
          "csInquiryUnresolvedAlertThresholdHours": {
            "value": 24,
            "unit": "hours",
            "description": "미해결 고객 문의 알림 기준 시간"
          }
        }
        """)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "시스템 파라미터 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.SYSTEM_PARAMETER_BULK_UPDATE_INVALID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = OpenApiCommonResponses.ADMIN_UNAUTHORIZED_REF)})
    public ResponseEntity<ApiResponse<SystemParameterListResponse>> bulkUpdateSystemParameters(
        @AuthenticationPrincipal AdminPrincipal adminPrincipal,
        @Valid @RequestBody SystemParameterTypedUpdateRequest request, HttpServletRequest servletRequest) {
        AdminClientInfo clientInfo = adminClientInfoResolver.resolve(servletRequest);
        SystemParameterListResponse response = systemParameterService.bulkUpdate(adminPrincipal, request, clientInfo);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(BULK_UPDATE_SUCCESS_MESSAGE, response));
    }
}
