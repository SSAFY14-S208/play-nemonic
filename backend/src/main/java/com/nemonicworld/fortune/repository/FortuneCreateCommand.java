package com.nemonicworld.fortune.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 오늘의 운세 산출물과 갤러리 row를 저장하기 위한 명령 객체입니다.
 */
public record FortuneCreateCommand(UUID artifactId, UUID galleryId, UUID userId, LocalDate fortuneDate,
    String description, String fortuneImageObjectKey, String artifactMeta, LocalDateTime createdAt) {
}
