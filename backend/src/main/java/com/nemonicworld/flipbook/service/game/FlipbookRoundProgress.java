package com.nemonicworld.flipbook.service.game;

/**
 * 플립북 현재 라운드의 제출 완료 진행도를 담습니다.
 */
public record FlipbookRoundProgress(int submittedCount, int totalCount, boolean currentRoundCompleted) {
}
