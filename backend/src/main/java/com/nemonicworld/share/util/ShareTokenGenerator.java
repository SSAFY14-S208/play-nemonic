package com.nemonicworld.share.util;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ShareTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 18;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * DB에 저장하지 않고 로그끼리 연결하기 위한 URL-safe 랜덤 토큰을 생성합니다.
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
