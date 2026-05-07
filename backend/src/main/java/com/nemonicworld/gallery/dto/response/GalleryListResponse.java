package com.nemonicworld.gallery.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 갤러리 목록 조회 응답")
/**
 * UUID 소유자가 보관 중인 결과물 목록과 페이지 정보를 함께 반환하는 응답 DTO입니다.
 */
public record GalleryListResponse(@Schema(description = "갤러리 결과물 목록") List<GalleryItemResponse> items,
    @Schema(description = "현재 페이지 번호", example = "0") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 결과물 수", example = "1") long totalElements,
    @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext) {
}
