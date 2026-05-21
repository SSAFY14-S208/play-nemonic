package com.nemonicworld.backoffice.infinitecanvas.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 무한 캔버스 강제 종료 응답")
public record BackofficeInfiniteCanvasCloseResponse(@Schema(description = "강제 종료한 공유 방코드") String roomCode) {
}
