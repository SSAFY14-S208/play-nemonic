package com.nemonicworld.user.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.user.dto.request.AnonymousUserBirthInfoRequest;
import com.nemonicworld.user.dto.request.AnonymousUserNicknameRequest;
import com.nemonicworld.user.dto.response.AnonymousUserBirthInfoResponse;
import com.nemonicworld.user.dto.response.AnonymousUserNicknameResponse;
import com.nemonicworld.user.dto.response.AnonymousUserProfileResponse;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.dto.response.AnonymousUserVerifyResponse;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
/**
 * 익명 사용자 유스케이스를 처리하는 서비스입니다.
 *
 * UUID 발급, 기존 UUID 검증, 닉네임 설정/수정 흐름을 담당합니다.
 */
public class UserService {

    // User-Agent는 선택 헤더이므로 수집하지 못한 경우 명시적인 기본값으로 저장합니다.
    private static final String UNKNOWN_USER_AGENT = "unknown";
    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";
    private static final String INVALID_NICKNAME_MESSAGE = "닉네임은 1자 이상 10자 이하로 입력해주세요.";
    private static final String INVALID_BIRTH_INFO_MESSAGE = "생년월일 정보 형식이 올바르지 않습니다.";
    private static final String BIRTH_INFO_ALREADY_REGISTERED_MESSAGE = "이미 생년월일 정보가 등록되어 있습니다.";
    private static final String BIRTH_INFO_NOT_REGISTERED_MESSAGE = "등록된 생년월일 정보가 없습니다.";
    private static final int MAX_NICKNAME_CODE_POINTS = 10;
    private static final DateTimeFormatter BIRTHTIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 매 호출마다 새로운 익명 사용자를 생성하고 저장합니다.
     *
     * 클라이언트 UUID를 입력받지 않고 서버가 UUID를 직접 발급합니다.
     */
    @Transactional
    public AnonymousUserResponse createAnonymousUser(String userAgent) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(UUID.randomUUID(), normalizeUserAgent(userAgent), now);
        AppUser savedUser = userRepository.save(appUser);

        return new AnonymousUserResponse(savedUser.getId().toString(), savedUser.getNickname(),
            savedUser.getCreatedAt());
    }

    /**
     * 클라이언트가 보관 중인 익명 사용자 UUID를 검증하고 재방문 정보를 갱신합니다.
     */
    @Transactional
    public AnonymousUserVerifyResponse verifyAnonymousUser(String userUuidValue, String userAgent) {
        UUID userUuid = parseUserUuid(userUuidValue);
        AppUser appUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        appUser.updateLastSeen(normalizeUserAgent(userAgent), now);

        return new AnonymousUserVerifyResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getLastSeenAt());
    }

    /**
     * 서버에 등록된 익명 사용자의 닉네임을 설정하거나 수정합니다.
     */
    @Transactional
    public AnonymousUserNicknameResponse updateAnonymousUserNickname(String userUuidValue,
        AnonymousUserNicknameRequest request) {
        UUID userUuid = parseUserUuid(userUuidValue);
        // 닉네임 안의 공백은 허용하므로 저장 전 trim하지 않고 원문을 유지합니다.
        String nickname = request == null ? null : request.nickname();

        validateNickname(nickname);

        AppUser appUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        appUser.updateNickname(nickname, now);

        return new AnonymousUserNicknameResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getUpdatedAt());
    }

    /**
     * 운세 기능 최초 진입 시 필요한 생년월일 정보를 등록합니다.
     */
    @Transactional
    public AnonymousUserBirthInfoResponse registerAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request) {
        UUID userUuid = parseUserUuid(userUuidValue);
        BirthInfo birthInfo = parseBirthInfo(request);
        AppUser appUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));

        if (appUser.hasBirthInfo()) {
            throw new ConflictException(BIRTH_INFO_ALREADY_REGISTERED_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        appUser.updateBirthInfo(birthInfo.birthday(), birthInfo.birthtime(), birthInfo.isLunar(), now);

        return toBirthInfoResponse(appUser);
    }

    /**
     * 이미 등록된 생년월일 정보를 사용자의 요청 값으로 수정합니다.
     */
    @Transactional
    public AnonymousUserBirthInfoResponse updateAnonymousUserBirthInfo(String userUuidValue,
        AnonymousUserBirthInfoRequest request) {
        UUID userUuid = parseUserUuid(userUuidValue);
        BirthInfo birthInfo = parseBirthInfo(request);
        AppUser appUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));

        if (!appUser.hasBirthInfo()) {
            throw new NotFoundException(BIRTH_INFO_NOT_REGISTERED_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        appUser.updateBirthInfo(birthInfo.birthday(), birthInfo.birthtime(), birthInfo.isLunar(), now);

        return toBirthInfoResponse(appUser);
    }

    /**
     * 서버에 등록된 익명 사용자의 프로필을 읽기 전용으로 조회합니다.
     */
    @Transactional(readOnly = true)
    public AnonymousUserProfileResponse getAnonymousUserProfile(String userUuidValue) {
        UUID userUuid = parseUserUuid(userUuidValue);
        AppUser appUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));

        return new AnonymousUserProfileResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getBirthday(), appUser.getBirthtime(), appUser.getIsLunar(), appUser.getCreatedAt(),
            appUser.getUpdatedAt(), appUser.getLastSeenAt());
    }

    private UUID parseUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        try {
            return UUID.fromString(userUuid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
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

    private BirthInfo parseBirthInfo(AnonymousUserBirthInfoRequest request) {
        if (request == null) {
            throw new BadRequestException(INVALID_BIRTH_INFO_MESSAGE);
        }

        return new BirthInfo(parseBirthday(request.birthday()), parseBirthtime(request.birthtime()),
            parseIsLunar(request.isLunar()));
    }

    private LocalDate parseBirthday(String birthday) {
        if (!StringUtils.hasText(birthday)) {
            throw new BadRequestException(INVALID_BIRTH_INFO_MESSAGE);
        }

        try {
            return LocalDate.parse(birthday);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(INVALID_BIRTH_INFO_MESSAGE);
        }
    }

    private LocalTime parseBirthtime(String birthtime) {
        if (!StringUtils.hasText(birthtime)) {
            throw new BadRequestException(INVALID_BIRTH_INFO_MESSAGE);
        }

        try {
            return LocalTime.parse(birthtime, BIRTHTIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(INVALID_BIRTH_INFO_MESSAGE);
        }
    }

    private Boolean parseIsLunar(Boolean isLunar) {
        if (isLunar == null) {
            throw new BadRequestException(INVALID_BIRTH_INFO_MESSAGE);
        }

        return isLunar;
    }

    private AnonymousUserBirthInfoResponse toBirthInfoResponse(AppUser appUser) {
        return new AnonymousUserBirthInfoResponse(appUser.getId().toString(), appUser.getBirthday(),
            appUser.getBirthtime(), appUser.getIsLunar(), appUser.getUpdatedAt());
    }

    /**
     * User-Agent가 없거나 공백이면 DB의 NOT NULL 제약을 만족하도록 unknown으로 정규화합니다.
     */
    private String normalizeUserAgent(String userAgent) {
        if (StringUtils.hasText(userAgent)) {
            return userAgent;
        }

        return UNKNOWN_USER_AGENT;
    }

    private record BirthInfo(LocalDate birthday, LocalTime birthtime, Boolean isLunar) {
    }
}
