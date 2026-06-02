package com.nemonicworld.user.service.profile;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.user.dto.request.AnonymousUserNicknameRequest;
import com.nemonicworld.user.dto.response.AnonymousUserNicknameResponse;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AnonymousUserNicknameUseCase {

    private static final String INVALID_NICKNAME_MESSAGE = "닉네임은 1자 이상 10자 이하로 입력해주세요.";
    private static final int MAX_NICKNAME_CODE_POINTS = 10;

    private final AnonymousUserResolver anonymousUserResolver;

    public AnonymousUserNicknameUseCase(AnonymousUserResolver anonymousUserResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
    }

    @Transactional
    public AnonymousUserNicknameResponse updateAnonymousUserNickname(String userUuidValue,
        AnonymousUserNicknameRequest request) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        // 닉네임 안의 공백은 허용하므로 저장 전 trim하지 않고 원문을 유지합니다.
        String nickname = request == null ? null : request.nickname();

        validateNickname(nickname);

        AppUser appUser = anonymousUserResolver.resolve(userUuid);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        appUser.updateNickname(nickname, now);

        return new AnonymousUserNicknameResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getUpdatedAt());
    }

    private void validateNickname(String nickname) {
        if (!StringUtils.hasText(nickname) || isNicknameTooLong(nickname)) {
            throw new BadRequestException(INVALID_NICKNAME_MESSAGE);
        }
    }

    // 이모지를 Java char 2개로 과계산하지 않도록 사용자 기준에 더 가까운 code point 개수로 길이를 봅니다.
    private boolean isNicknameTooLong(String nickname) {
        return nickname.codePointCount(0, nickname.length()) > MAX_NICKNAME_CODE_POINTS;
    }
}
