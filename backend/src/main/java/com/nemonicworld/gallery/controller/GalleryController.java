package com.nemonicworld.gallery.controller;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.openapi.OpenApiErrorExamples;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.gallery.dto.response.GalleryDeleteResponse;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;
import com.nemonicworld.gallery.phone.dto.request.PhoneDrawingSaveRequest;
import com.nemonicworld.gallery.phone.dto.response.PhoneDrawingSaveResponse;
import com.nemonicworld.gallery.phone.service.PhoneDrawingService;
import com.nemonicworld.gallery.service.GalleryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gallery")
@Tag(name = "Gallery", description = "갤러리 API")
/**
 * UUID 기반 내 갤러리 조회 요청을 처리하는 컨트롤러입니다.
 */
public class GalleryController {

    private static final String ANONYMOUS_USER_UUID_HEADER = AnonymousUserHeaders.ANONYMOUS_USER_UUID;
    private static final String MY_GALLERY_FOUND_MESSAGE = "내 갤러리 목록 조회 성공";
    private static final String GALLERY_ITEM_DETAIL_FOUND_MESSAGE = "내 갤러리 항목 상세 조회 성공";
    private static final String GALLERY_ITEM_DELETED_MESSAGE = "갤러리 항목 삭제 성공";
    private static final String PHONE_DRAWING_SAVED_MESSAGE = "휴대폰 그림 갤러리 저장 성공";

    private final GalleryService galleryService;
    private final PhoneDrawingService phoneDrawingService;

    public GalleryController(GalleryService galleryService, PhoneDrawingService phoneDrawingService) {
        this.galleryService = galleryService;
        this.phoneDrawingService = phoneDrawingService;
    }

    /**
     * 익명 사용자의 보관 결과물 목록을 최신순으로 조회합니다.
     */
    @GetMapping
    @Operation(summary = "내 갤러리 목록 조회", description = "UUID에 저장된 내 결과물 목록을 산출물 생성 시각 기준 최신순으로 조회합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호, 기본값 0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기, 기본값 20, 최대 50")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 갤러리 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "페이지 요청 오류", value = OpenApiErrorExamples.INVALID_PAGE_REQUEST)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.USER_NOT_FOUND)))})
    public ResponseEntity<ApiResponse<GalleryListResponse>> getMyGallery(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestParam(name = "page", required = false, defaultValue = "0") String page,
        @RequestParam(name = "size", required = false, defaultValue = "20") String size) {
        GalleryListResponse response = galleryService.getMyGallery(userUuid, page, size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(MY_GALLERY_FOUND_MESSAGE, response));
    }

    /**
     * 휴대폰 모달에서 만든 그림을 phone 산출물로 저장하고 내 갤러리에 추가합니다.
     */
    @PostMapping("/drawings")
    @Operation(summary = "휴대폰 그림 갤러리 추가", description = "휴대폰 모달에서 그린 이미지를 phone 산출물로 저장하고 내 갤러리에 추가합니다.")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true, description = "익명 사용자 UUID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "휴대폰 그림 갤러리 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.INVALID_UUID))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "파일 접근 권한 없음", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_ACCESS_DENIED))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 파일 없음", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "파일 없음", value = OpenApiErrorExamples.FILE_UPLOAD_NOT_FOUND)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "파일 업로드 상태 충돌", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = OpenApiErrorExamples.FILE_UPLOAD_STATUS_CONFLICT)))})
    public ResponseEntity<ApiResponse<PhoneDrawingSaveResponse>> addPhoneDrawingToGallery(
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid,
        @RequestBody(required = false) PhoneDrawingSaveRequest request) {
        PhoneDrawingSaveResponse response = phoneDrawingService.savePhoneDrawing(userUuid, request);

        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(PHONE_DRAWING_SAVED_MESSAGE, response));
    }

    /**
     * 익명 사용자의 보관 결과물 한 건을 상세 화면용 데이터로 조회합니다.
     */
    @GetMapping("/{galleryId}")
    @Operation(summary = "내 갤러리 항목 상세 조회", description = "UUID 소유자의 갤러리 항목 한 건을 상세 화면에 필요한 URL과 메타데이터까지 조회합니다.")
    @Parameter(name = "galleryId", in = ParameterIn.PATH, required = true, description = "조회할 갤러리 항목 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 갤러리 항목 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "갤러리 항목 ID 형식 오류", value = OpenApiErrorExamples.INVALID_GALLERY_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 갤러리 항목", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "갤러리 항목 없음", value = OpenApiErrorExamples.GALLERY_ITEM_NOT_FOUND)}))})
    public ResponseEntity<ApiResponse<GalleryDetailResponse>> getMyGalleryItemDetail(
        @PathVariable("galleryId") String galleryId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        GalleryDetailResponse response = galleryService.getMyGalleryItemDetail(userUuid, galleryId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(GALLERY_ITEM_DETAIL_FOUND_MESSAGE, response));
    }

    /**
     * 익명 사용자의 보관함에서 갤러리 항목만 제거합니다.
     */
    @DeleteMapping("/{galleryId}")
    @Operation(summary = "갤러리 항목 삭제", description = "원본 결과물은 보존하고 내 갤러리 보관함에서만 항목을 제거합니다.")
    @Parameter(name = "galleryId", in = ParameterIn.PATH, required = true, description = "삭제할 갤러리 항목 ID")
    @Parameter(name = ANONYMOUS_USER_UUID_HEADER, in = ParameterIn.HEADER, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "갤러리 항목 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "UUID 형식 오류", value = OpenApiErrorExamples.INVALID_UUID),
            @ExampleObject(name = "갤러리 항목 ID 형식 오류", value = OpenApiErrorExamples.INVALID_GALLERY_ID)})),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자 또는 갤러리 항목", content = @Content(mediaType = "application/json", examples = {
            @ExampleObject(name = "사용자 없음", value = OpenApiErrorExamples.USER_NOT_FOUND),
            @ExampleObject(name = "갤러리 항목 없음", value = OpenApiErrorExamples.GALLERY_ITEM_NOT_FOUND)}))})
    public ResponseEntity<ApiResponse<GalleryDeleteResponse>> deleteMyGalleryItem(
        @PathVariable("galleryId") String galleryId,
        @RequestHeader(value = ANONYMOUS_USER_UUID_HEADER, required = false) String userUuid) {
        GalleryDeleteResponse response = galleryService.deleteMyGalleryItem(userUuid, galleryId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(GALLERY_ITEM_DELETED_MESSAGE, response));
    }
}
