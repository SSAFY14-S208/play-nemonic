package com.nemonicworld.fortune.service;

import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.dto.response.FortuneResponse;

public interface FortuneService {

    FortuneAvailabilityResponse getTodayAvailability(String userUuidValue);

    FortuneResponse getTodayFortune(String userUuidValue);

    FortuneResponse createFortune(String userUuidValue, FortuneCreateRequest request);
}
