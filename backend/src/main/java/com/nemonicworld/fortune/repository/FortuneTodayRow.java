package com.nemonicworld.fortune.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 오늘의 운세 생성 가능 여부 판단에 필요한 최소 조회 결과입니다.
 */
public record FortuneTodayRow(UUID fortuneId, LocalDate fortuneDate, LocalDateTime createdAt) {
}
