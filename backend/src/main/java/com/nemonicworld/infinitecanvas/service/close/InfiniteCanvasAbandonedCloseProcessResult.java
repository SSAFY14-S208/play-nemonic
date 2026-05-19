package com.nemonicworld.infinitecanvas.service.close;

/**
 * 한 번의 스케줄러 tick에서 {@code findAbandonedActiveCanvases}로 후보를 가져와
 * 닫기까지 진행한 결과 요약. 릴레이/플립북의 {@code AbandonedCloseProcessResult} 패턴과 동일.
 */
public record InfiniteCanvasAbandonedCloseProcessResult(int candidateCount, int closedCount) {
}
