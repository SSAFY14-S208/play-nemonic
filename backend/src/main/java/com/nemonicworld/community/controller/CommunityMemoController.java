package com.nemonicworld.community.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.service.CommunityMemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/memos")
@Tag(name = "Community", description = "커뮤니티 API")
/**
 * 커뮤니티 캔버스 공용 벽 메모 조회 요청을 처리하는 컨트롤러입니다.
 */
public class CommunityMemoController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String COMMUNITY_MEMOS_FOUND_MESSAGE = "커뮤니티 메모 목록 조회 성공";

    private final CommunityMemoService communityMemoService;

    public CommunityMemoController(CommunityMemoService communityMemoService) {
        this.communityMemoService = communityMemoService;
    }

    /**
     * 공용 벽에 노출 가능한 커뮤니티 메모를 z-index와 부착 시각 순서로 조회합니다.
     */
    @GetMapping
    @Operation(summary = "커뮤니티 메모 목록 조회", description = "공용 벽에 노출 가능한 커뮤니티 메모 목록을 조회합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = false)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID)))})
    public ResponseEntity<ApiResponse<CommunityMemoListResponse>> getCommunityMemos(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        CommunityMemoListResponse response = communityMemoService.getCommunityMemos(userUuid);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(COMMUNITY_MEMOS_FOUND_MESSAGE, response));
    }
}
