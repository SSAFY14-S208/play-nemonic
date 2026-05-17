package com.nemonicworld.infinitecanvas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 내 색상 수정 요청")
public record InfiniteCanvasColorUpdateRequest(@Schema(description = "변경할 참여자 색상", example = "#72DDF7") String color) {
}
