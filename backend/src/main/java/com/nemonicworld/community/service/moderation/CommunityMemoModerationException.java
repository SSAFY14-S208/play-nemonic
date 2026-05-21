package com.nemonicworld.community.service.moderation;

public class CommunityMemoModerationException extends RuntimeException {

    /**
     * 모더레이션 실패 메시지만 담은 예외를 생성합니다.
     */
    public CommunityMemoModerationException(String message) {
        super(message);
    }

    /**
     * 모더레이션 실패 메시지와 원인 예외를 함께 담은 예외를 생성합니다.
     */
    public CommunityMemoModerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
