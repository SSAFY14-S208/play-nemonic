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

    /**
     * 신규 익명 사용자를 생성하고 서버가 발급한 UUID를 반환합니다.
     */
    AnonymousUserResponse createAnonymousUser(String userAgent);

    /**
     * 클라이언트가 보관 중인 익명 사용자 UUID를 검증하고 재방문 정보를 갱신합니다.
     */
    AnonymousUserVerifyResponse verifyAnonymousUser(String userUuidValue, String userAgent);

    /**
     * 익명 사용자의 닉네임을 설정하거나 수정합니다.
     */
    AnonymousUserNicknameResponse updateAnonymousUserNickname(String userUuidValue,
        AnonymousUserNicknameRequest request);

    /**
     * 운세 기능에서 사용할 생년월일 정보를 최초 등록합니다.
     */
    AnonymousUserBirthInfoResponse registerAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request);

    /**
     * 이미 등록된 생년월일 정보를 수정합니다.
     */
    AnonymousUserBirthInfoResponse updateAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request);

    /**
     * 익명 사용자의 재사용 가능 프로필 정보를 조회합니다.
     */
    AnonymousUserProfileResponse getAnonymousUserProfile(String userUuidValue);

}
