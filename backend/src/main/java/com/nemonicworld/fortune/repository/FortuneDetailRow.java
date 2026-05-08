package com.nemonicworld.fortune.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 저장된 오늘의 운세 결과 재조회에 필요한 상세 조회 결과입니다.
 */
public record FortuneDetailRow(UUID fortuneId, LocalDate fortuneDate, String description, LocalDateTime createdAt) {
}
