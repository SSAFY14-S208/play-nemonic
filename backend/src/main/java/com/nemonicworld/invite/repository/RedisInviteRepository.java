package com.nemonicworld.invite.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.invite.redis.InviteMetadata;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 초대코드 메타데이터를 Redis 문자열 JSON 값으로 조회하는 저장소입니다.
 */
@Repository
public class RedisInviteRepository implements InviteRepository {

    private static final String INVITE_KEY_PREFIX = "invite:";
    private static final String INVITE_SERIALIZATION_ERROR_MESSAGE = "초대코드 정보를 저장할 수 없습니다.";
    private static final String INVITE_DESERIALIZATION_ERROR_MESSAGE = "초대코드 정보를 읽을 수 없습니다.";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisInviteRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 전체 부스가 공유하는 초대코드 충돌 방지 기준으로 Redis key 존재 여부를 확인합니다.
     */
    @Override
    public boolean existsByInviteCode(String inviteCode) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createInviteKey(inviteCode)));
    }

    /**
     * 초대코드 메타데이터를 JSON 문자열로 변환해 Redis에 TTL과 함께 저장합니다.
     */
    @Override
    public void save(InviteMetadata inviteMetadata, Duration ttl) {
        redisTemplate.opsForValue().set(createInviteKey(inviteMetadata.inviteCode()), serialize(inviteMetadata), ttl);
    }

    /**
     * Redis의 invite:{inviteCode} 값을 초대 메타데이터로 복원합니다.
     */
    @Override
    public Optional<InviteMetadata> findByInviteCode(String inviteCode) {
        String inviteValue = redisTemplate.opsForValue().get(createInviteKey(inviteCode));

        if (!StringUtils.hasText(inviteValue)) { // inviteValue가 null이거나 빈 문자열이나 공백이면
            return Optional.empty(); // 값이 없음
        }

        return Optional.of(deserialize(inviteValue)); // inviteValue는 json문자열 (redis값) -> 역직렬화해서 객체화 -> 객체 반환
    }

    private String createInviteKey(String inviteCode) {
        return INVITE_KEY_PREFIX + inviteCode;
    }

    /**
     * 문자열 직접 조립 대신 Jackson으로 Redis 저장 JSON을 생성합니다.
     */
    private String serialize(InviteMetadata inviteMetadata) {
        try {
            return objectMapper.writeValueAsString(inviteMetadata);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(INVITE_SERIALIZATION_ERROR_MESSAGE, e);
        }
    }

    /**
     * Redis 문자열 JSON을 초대 메타데이터 모델로 복원합니다. (Redis에 문자열로 저장된 JSON을 Java 객체로 객체화, 즉
     * 역직렬화(deserialize)하는 코드)
     */
    private InviteMetadata deserialize(String inviteValue) {
        try {
            return objectMapper.readValue(inviteValue, InviteMetadata.class);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(INVITE_DESERIALIZATION_ERROR_MESSAGE, e);
        }
    }
}
