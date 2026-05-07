package com.nemonicworld.invite.redis;

import java.time.LocalDateTime;

/**
 * Redis invite:{inviteCode} value로 저장되는 초대코드 메타데이터입니다.
 *
 * <p>
 * PostgreSQL 테이블과 매핑되는 JPA Entity가 아니라 Redis JSON value를 역직렬화하기 위한 저장 모델입니다.
 */
public record InviteMetadata(String inviteCode, String boothType, String roomId, String roomName,
    LocalDateTime expiresAt) {
}
