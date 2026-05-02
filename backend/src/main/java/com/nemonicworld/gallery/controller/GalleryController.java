package com.nemonicworld.gallery.controller;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.gallery.dto.response.GalleryDeleteResponse;
import com.nemonicworld.gallery.dto.response.GalleryListResponse;
import com.nemonicworld.gallery.service.GalleryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gallery")
@Tag(name = "Gallery", description = "갤러리 API")
/**
 * UUID 기반 내 갤러리 조회 요청을 처리하는 컨트롤러입니다.
 */
public class GalleryController {

    private static final String MY_GALLERY_FOUND_MESSAGE = "내 갤러리 목록 조회 성공";
    private static final String GALLERY_ITEM_DELETED_MESSAGE = "갤러리 항목 삭제 성공";

    private final GalleryService galleryService;

    public GalleryController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    /**
     * 익명 사용자의 보관 결과물 목록을 최신순으로 조회합니다.
     */
    @GetMapping
    @Operation(summary = "내 갤러리 목록 조회", description = "UUID에 저장된 내 결과물 목록을 artifact 생성 시각 기준 최신순으로 조회합니다.")
    @Parameter(name = "userUuid", in = ParameterIn.QUERY, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 갤러리 목록 조회 성공")
    public ResponseEntity<ApiResponse<GalleryListResponse>> getMyGallery(
        @RequestParam(required = false) String userUuid,
        @RequestParam(required = false, defaultValue = "0") String page,
        @RequestParam(required = false, defaultValue = "20") String size) {
        GalleryListResponse response = galleryService.getMyGallery(userUuid, page, size);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(MY_GALLERY_FOUND_MESSAGE, response));
    }

    /**
     * 익명 사용자의 보관함에서 갤러리 항목만 제거합니다.
     */
    @DeleteMapping("/{galleryId}")
    @Operation(summary = "갤러리 항목 삭제", description = "원본 결과물은 보존하고 내 갤러리 보관함에서만 항목을 제거합니다.")
    @Parameter(name = "galleryId", in = ParameterIn.PATH, required = true, description = "삭제할 갤러리 항목 ID")
    @Parameter(name = "userUuid", in = ParameterIn.QUERY, required = true, description = "서버가 발급한 익명 사용자 UUID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "갤러리 항목 삭제 성공")
    public ResponseEntity<ApiResponse<GalleryDeleteResponse>> deleteMyGalleryItem(@PathVariable String galleryId,
        @RequestParam(required = false) String userUuid) {
        GalleryDeleteResponse response = galleryService.deleteMyGalleryItem(userUuid, galleryId);

        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(GALLERY_ITEM_DELETED_MESSAGE, response));
    }
}
