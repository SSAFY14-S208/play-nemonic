package com.nemonicworld.user.service;

import com.nemonicworld.user.dto.request.AnonymousUserBirthInfoRequest;
import com.nemonicworld.user.dto.request.AnonymousUserNicknameRequest;
import com.nemonicworld.user.dto.response.AnonymousUserBirthInfoResponse;
import com.nemonicworld.user.dto.response.AnonymousUserNicknameResponse;
import com.nemonicworld.user.dto.response.AnonymousUserProfileResponse;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.dto.response.AnonymousUserVerifyResponse;
import com.nemonicworld.user.service.profile.AnonymousUserBirthInfoUseCase;
import com.nemonicworld.user.service.profile.AnonymousUserNicknameUseCase;
import com.nemonicworld.user.service.user.AnonymousUserCreateUseCase;
import com.nemonicworld.user.service.user.AnonymousUserProfileUseCase;
import com.nemonicworld.user.service.user.AnonymousUserVerifyUseCase;
import org.springframework.stereotype.Service;

/**
 * 익명 사용자 유스케이스를 처리하는 서비스입니다.
 *
 * UUID 발급, 기존 UUID 검증, 닉네임 설정/수정 흐름을 담당합니다.
 */
@Service
public class UserServiceImpl implements UserService {

    private final AnonymousUserCreateUseCase anonymousUserCreateUseCase;
    private final AnonymousUserVerifyUseCase anonymousUserVerifyUseCase;
    private final AnonymousUserNicknameUseCase anonymousUserNicknameUseCase;
    private final AnonymousUserBirthInfoUseCase anonymousUserBirthInfoUseCase;
    private final AnonymousUserProfileUseCase anonymousUserProfileUseCase;

    public UserServiceImpl(AnonymousUserCreateUseCase anonymousUserCreateUseCase,
        AnonymousUserVerifyUseCase anonymousUserVerifyUseCase,
        AnonymousUserNicknameUseCase anonymousUserNicknameUseCase,
        AnonymousUserBirthInfoUseCase anonymousUserBirthInfoUseCase,
        AnonymousUserProfileUseCase anonymousUserProfileUseCase) {
        this.anonymousUserCreateUseCase = anonymousUserCreateUseCase;
        this.anonymousUserVerifyUseCase = anonymousUserVerifyUseCase;
        this.anonymousUserNicknameUseCase = anonymousUserNicknameUseCase;
        this.anonymousUserBirthInfoUseCase = anonymousUserBirthInfoUseCase;
        this.anonymousUserProfileUseCase = anonymousUserProfileUseCase;
    }

    /**
     * 매 호출마다 새로운 익명 사용자를 생성하고 저장합니다.
     *
     * 클라이언트 UUID를 입력받지 않고 서버가 UUID를 직접 발급합니다.
     */
    @Override
    public AnonymousUserResponse createAnonymousUser(String userAgent) {
        return anonymousUserCreateUseCase.createAnonymousUser(userAgent);
    }

    /**
     * 클라이언트가 보관 중인 익명 사용자 UUID를 검증하고 재방문 정보를 갱신합니다.
     */
    @Override
    public AnonymousUserVerifyResponse verifyAnonymousUser(String userUuidValue, String userAgent) {
        return anonymousUserVerifyUseCase.verifyAnonymousUser(userUuidValue, userAgent);
    }

    /**
     * 서버에 등록된 익명 사용자의 닉네임을 설정하거나 수정합니다.
     */
    @Override
    public AnonymousUserNicknameResponse updateAnonymousUserNickname(String userUuidValue,
        AnonymousUserNicknameRequest request) {
        return anonymousUserNicknameUseCase.updateAnonymousUserNickname(userUuidValue, request);
    }

    /**
     * 운세 기능 최초 진입 시 필요한 생년월일 정보를 등록합니다.
     */
    @Override
    public AnonymousUserBirthInfoResponse registerAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request) {
        return anonymousUserBirthInfoUseCase.registerAnonymousUserBirthInfo(userUuidValue, request);
    }

    /**
     * 이미 등록된 생년월일 정보를 사용자의 요청 값으로 수정합니다.
     */
    @Override
    public AnonymousUserBirthInfoResponse updateAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request) {
        return anonymousUserBirthInfoUseCase.updateAnonymousUserBirthInfo(userUuidValue, request);
    }

    /**
     * 서버에 등록된 익명 사용자의 프로필을 읽기 전용으로 조회합니다.
     */
    @Override
    public AnonymousUserProfileResponse getAnonymousUserProfile(String userUuidValue) {
        return anonymousUserProfileUseCase.getAnonymousUserProfile(userUuidValue);
    }
}
