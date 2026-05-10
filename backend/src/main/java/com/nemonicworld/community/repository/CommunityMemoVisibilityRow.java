package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * visible 조건에서 제외된 메모 접근을 운영 로그에 남기기 위한 최소 상태 projection입니다.
 */
public record CommunityMemoVisibilityRow(UUID memoId, UUID userId, boolean hidden, LocalDateTime deletedAt) {
}
