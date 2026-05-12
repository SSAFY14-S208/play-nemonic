package com.nemonicworld.community.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.community.dto.request.CommunityMemoCreateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoLayoutUpdateRequest;
import com.nemonicworld.community.dto.request.CommunityMemoReportRequest;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoListResponse;
import com.nemonicworld.community.dto.response.CommunityMemoReportResponse;
import com.nemonicworld.community.service.CommunityMemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private static final String COMMUNITY_MEMO_FOUND_MESSAGE = "커뮤니티 메모 상세 조회 성공";
    private static final String COMMUNITY_MEMO_CREATED_MESSAGE = "커뮤니티 메모 생성 성공";
    private static final String COMMUNITY_MEMO_UPDATED_MESSAGE = "커뮤니티 메모 위치 수정 성공";
    private static final String COMMUNITY_MEMO_DELETED_MESSAGE = "커뮤니티 메모 삭제 성공";
    private static final String COMMUNITY_MEMO_REPORTED_MESSAGE = "커뮤니티 메모 신고 성공";

    private final CommunityMemoService communityMemoService;

    /**
     * 커뮤니티 메모 API가 사용할 서비스 의존성을 주입합니다.
     */
    public CommunityMemoController(CommunityMemoService communityMemoService) {
        this.communityMemoService = communityMemoService;
    }

    /**
     * 업로드/confirm 완료된 최종 원본·썸네일 스냅샷으로 커뮤니티 메모를 생성합니다.
     */
    @PostMapping
    @Operation(summary = "커뮤니티 메모 생성", description = "파일 API로 업로드와 완료 확인을 끝낸 최종 원본/썸네일 스냅샷을 커뮤니티 메모로 생성합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @RequestBody(required = true, content = @Content(examples = @ExampleObject(value = """
        {
          "sourceType": "DIRECT",
          "originalFileId": "550e8400-e29b-41d4-a716-446655440000",
          "thumbnailFileId": "660e8400-e29b-41d4-a716-446655440000",
          "sourceGalleryId": null,
          "positionX": 0.0,
          "positionY": 0.0,
          "zIndex": 1,
          "rotationDeg": 0.0,
          "decoration": {
            "layers": []
          },
          "clientText": "텍스트박스 원문"
        }
        """)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "커뮤니티 메모 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값 오류", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "원본 정보 오류", value = OpenApiErrorExamples.INVALID_COMMUNITY_MEMO_SOURCE),
            @ExampleObject(name = "originalFileId 형식 오류", value = OpenApiErrorExamples.INVALID_ORIGINAL_FILE_ID),
            @ExampleObject(name = "thumbnailFileId 형식 오류", value = OpenApiErrorExamples.INVALID_THUMBNAIL_FILE_ID),
            @ExampleObject(name = "sourceGalleryId 형식 오류", value = OpenApiErrorExamples.INVALID_SOURCE_GALLERY_ID),
            @ExampleObject(name = "원본/썸네일 파일 중복", value = OpenApiErrorExamples.DUPLICATED_COMMUNITY_MEMO_FILE),
            @ExampleObject(name = "위치 정보 오류", value = OpenApiErrorExamples.INVALID_COMMUNITY_MEMO_POSITION),
            @ExampleObject(name = "데코레이션 정보 오류", value = OpenApiErrorExamples.INVALID_COMMUNITY_MEMO_DECORATION),
            @ExampleObject(name = "모더레이션 차단", value = OpenApiErrorExamples.COMMUNITY_MEMO_MODERATION_BLOCKED),
            @ExampleObject(name = "모더레이션 실패", value = OpenApiErrorExamples.COMMUNITY_MEMO_MODERATION_UNAVAILABLE)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "파일 접근 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 파일 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "파일 없음", value = OpenApiErrorExamples.FILE_UPLOAD_NOT_FOUND),
            @ExampleObject(name = "갤러리 항목 없음", value = OpenApiErrorExamples.GALLERY_ITEM_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "파일 업로드 상태 오류", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_UPLOAD_STATUS_CONFLICT)))})
    public ResponseEntity<ApiResponse<CommunityMemoDetailResponse>> createCommunityMemo(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @Valid @org.springframework.web.bind.annotation.RequestBody CommunityMemoCreateRequest request) {
        CommunityMemoDetailResponse response = communityMemoService.createCommunityMemo(userUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(COMMUNITY_MEMO_CREATED_MESSAGE, response));
    }

    /**
     * 공용 벽에 노출 가능한 커뮤니티 메모 목록을 z-index와 부착 시각 순서로 조회합니다.
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

    /**
     * 공용 벽에 노출 가능한 커뮤니티 메모 한 건의 상세 정보를 조회합니다.
     */
    @GetMapping("/{memoId}")
    @Operation(summary = "커뮤니티 메모 상세 조회", description = "공용 벽에 노출 가능한 커뮤니티 메모 한 건의 상세 정보를 조회합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "조회할 커뮤니티 메모 UUID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = false)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 커뮤니티 메모", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<CommunityMemoDetailResponse>> getCommunityMemo(
        @PathVariable("memoId") String memoId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        CommunityMemoDetailResponse response = communityMemoService.getCommunityMemo(memoId, userUuid);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(COMMUNITY_MEMO_FOUND_MESSAGE, response));
    }

    /**
     * 본인 visible 메모의 위치, z-index, 회전 각도만 수정합니다.
     */
    @PatchMapping("/{memoId}")
    @Operation(summary = "커뮤니티 메모 위치 수정", description = "본인 메모의 위치, z-index, 회전 각도만 수정합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "수정할 커뮤니티 메모 UUID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @RequestBody(required = true, content = @Content(examples = @ExampleObject(value = """
        {
          "positionX": 120.5,
          "positionY": -30.0,
          "zIndex": 12,
          "rotationDeg": 5.5
        }
        """)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 위치 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "위치 정보 오류", value = OpenApiErrorExamples.INVALID_COMMUNITY_MEMO_POSITION)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "커뮤니티 메모 위치 수정 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "메모 없음", value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)}))})
    public ResponseEntity<ApiResponse<CommunityMemoDetailResponse>> updateCommunityMemoLayout(
        @PathVariable("memoId") String memoId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @Valid @org.springframework.web.bind.annotation.RequestBody CommunityMemoLayoutUpdateRequest request) {
        CommunityMemoDetailResponse response = communityMemoService.updateCommunityMemoLayout(memoId, userUuid,
            request);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(COMMUNITY_MEMO_UPDATED_MESSAGE, response));
    }

    /**
     * 본인 visible 메모를 사용자 삭제 사유로 soft delete 합니다.
     */
    @DeleteMapping("/{memoId}")
    @Operation(summary = "커뮤니티 메모 삭제", description = "본인 visible 메모를 사용자 삭제 사유로 soft delete 합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "삭제할 커뮤니티 메모 UUID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "커뮤니티 메모 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "커뮤니티 메모 삭제 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.COMMUNITY_MEMO_DELETE_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "메모 없음", value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)}))})
    public ResponseEntity<ApiResponse<Void>> deleteCommunityMemo(@PathVariable("memoId") String memoId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        communityMemoService.deleteCommunityMemo(memoId, userUuid);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(COMMUNITY_MEMO_DELETED_MESSAGE, null));
    }

    /**
     * visible 메모를 신고하고, 누적 신고 5회 이상이면 자동 숨김 처리합니다.
     */
    @PostMapping("/{memoId}/reports")
    @Operation(summary = "커뮤니티 메모 신고", description = "표시 중인 커뮤니티 메모를 신고하고, 누적 신고 5회 이상이면 자동 숨김 처리합니다.")
    @Parameter(name = "memoId", in = ParameterIn.PATH, required = true, description = "신고할 커뮤니티 메모 UUID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @RequestBody(required = true, content = @Content(examples = @ExampleObject(value = """
        {
          "reason": "욕설/비방/혐오",
          "reasonDetail": "욕설이 포함되어 있어요."
        }
        """)))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "커뮤니티 메모 신고 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "신고 사유 오류", value = OpenApiErrorExamples.INVALID_COMMUNITY_MEMO_REPORT_REASON),
            @ExampleObject(name = "본인 메모 신고", value = OpenApiErrorExamples.OWN_COMMUNITY_MEMO_REPORT)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 커뮤니티 메모 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "메모 없음", value = OpenApiErrorExamples.COMMUNITY_MEMO_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 신고", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.DUPLICATE_COMMUNITY_MEMO_REPORT)))})
    public ResponseEntity<ApiResponse<CommunityMemoReportResponse>> reportCommunityMemo(
        @PathVariable("memoId") String memoId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @Valid @org.springframework.web.bind.annotation.RequestBody CommunityMemoReportRequest request) {
        CommunityMemoReportResponse response = communityMemoService.reportCommunityMemo(memoId, userUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(COMMUNITY_MEMO_REPORTED_MESSAGE, response));
    }
}
