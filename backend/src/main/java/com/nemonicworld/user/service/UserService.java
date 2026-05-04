package com.nemonicworld.user.service;

import com.nemonicworld.user.dto.request.AnonymousUserBirthInfoRequest;
import com.nemonicworld.user.dto.request.AnonymousUserNicknameRequest;
import com.nemonicworld.user.dto.response.AnonymousUserBirthInfoResponse;
import com.nemonicworld.user.dto.response.AnonymousUserNicknameResponse;
import com.nemonicworld.user.dto.response.AnonymousUserProfileResponse;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.dto.response.AnonymousUserVerifyResponse;

/**
 * 익명 사용자 발급, 검증, 프로필 관리 유스케이스를 정의합니다.
 */
public interface UserService {

    AnonymousUserResponse createAnonymousUser(String userAgent);

    AnonymousUserVerifyResponse verifyAnonymousUser(String userUuidValue, String userAgent);

    AnonymousUserNicknameResponse updateAnonymousUserNickname(String userUuidValue,
        AnonymousUserNicknameRequest request);

    AnonymousUserBirthInfoResponse registerAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request);

    AnonymousUserBirthInfoResponse updateAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request);

    AnonymousUserProfileResponse getAnonymousUserProfile(String userUuidValue);

}
