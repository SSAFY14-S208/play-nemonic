package com.nemonicworld.relay.service.game;

/**
 * 릴레이 현재 파트의 제출 완료 진행도를 담습니다.
 */
public record RelayPartProgress(int submittedCount, int totalCount, boolean currentPartCompleted) {
}
