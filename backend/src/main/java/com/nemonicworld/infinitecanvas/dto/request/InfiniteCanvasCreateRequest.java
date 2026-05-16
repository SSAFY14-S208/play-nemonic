package com.nemonicworld.infinitecanvas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 생성 요청. 닉네임은 Anonymous-User-UUID에 등록된 익명 사용자 닉네임을 사용합니다.")
public record InfiniteCanvasCreateRequest(
    @Schema(description = "참여자 색상. 생략하면 UUID 기반 기본 색상을 사용합니다.", nullable = true, example = "#2F80ED") String color) {
}
