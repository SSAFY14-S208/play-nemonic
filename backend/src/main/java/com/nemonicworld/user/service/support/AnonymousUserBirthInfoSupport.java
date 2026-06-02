package com.nemonicworld.user.service.support;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.user.dto.request.AnonymousUserBirthInfoRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnonymousUserBirthInfoSupport {

    private static final String INVALID_BIRTH_INFO_MESSAGE = "생년월일 정보 형식이 올바르지 않습니다.";
    private static final DateTimeFormatter BIRTHTIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public BirthInfo parseBirthInfo(AnonymousUserBirthInfoRequest request) {
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

    public record BirthInfo(LocalDate birthday, LocalTime birthtime, Boolean isLunar) {
    }
}
