package com.nemonicworld.invite.repository;

import com.nemonicworld.invite.redis.InviteMetadata;
import java.time.Duration;
import java.util.Optional;

public interface InviteRepository {

    /**
     * 새 초대코드 발급 때 같은 코드가 이미 사용 중인지 확인합니다.
     */
    boolean existsByInviteCode(String inviteCode);

    /**
     * 초대코드 메타데이터를 지정한 TTL 동안 저장합니다.
     */
    void save(InviteMetadata inviteMetadata, Duration ttl);

    /**
     * 초대코드로 연결된 부스와 방 메타데이터를 조회합니다.
     */
    Optional<InviteMetadata> findByInviteCode(String inviteCode);
}
