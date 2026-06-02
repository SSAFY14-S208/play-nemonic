package com.nemonicworld.fortune.service;

import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.dto.response.FortuneResponse;
import com.nemonicworld.fortune.service.fortune.FortuneAvailabilityUseCase;
import com.nemonicworld.fortune.service.fortune.FortuneCreateUseCase;
import com.nemonicworld.fortune.service.fortune.FortuneTodayQueryUseCase;
import org.springframework.stereotype.Service;

/**
 * 오늘의 운세 생성 가능 여부와 생성 요청을 처리하는 서비스입니다.
 */
@Service
public class FortuneServiceImpl implements FortuneService {

    private final FortuneAvailabilityUseCase fortuneAvailabilityUseCase;
    private final FortuneTodayQueryUseCase fortuneTodayQueryUseCase;
    private final FortuneCreateUseCase fortuneCreateUseCase;

    public FortuneServiceImpl(FortuneAvailabilityUseCase fortuneAvailabilityUseCase,
        FortuneTodayQueryUseCase fortuneTodayQueryUseCase, FortuneCreateUseCase fortuneCreateUseCase) {
        this.fortuneAvailabilityUseCase = fortuneAvailabilityUseCase;
        this.fortuneTodayQueryUseCase = fortuneTodayQueryUseCase;
        this.fortuneCreateUseCase = fortuneCreateUseCase;
    }

    @Override
    public FortuneAvailabilityResponse getTodayAvailability(String userUuidValue) {
        return fortuneAvailabilityUseCase.getTodayAvailability(userUuidValue);
    }

    @Override
    public FortuneResponse getTodayFortune(String userUuidValue) {
        return fortuneTodayQueryUseCase.getTodayFortune(userUuidValue);
    }

    @Override
    public FortuneResponse createFortune(String userUuidValue, FortuneCreateRequest request) {
        return fortuneCreateUseCase.createFortune(userUuidValue, request);
    }
}
