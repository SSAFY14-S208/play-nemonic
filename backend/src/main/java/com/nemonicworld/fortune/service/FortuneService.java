package com.nemonicworld.fortune.service;

import com.nemonicworld.fortune.dto.request.FortuneCreateRequest;
import com.nemonicworld.fortune.dto.response.FortuneAvailabilityResponse;
import com.nemonicworld.fortune.dto.response.FortuneCreateResponse;

public interface FortuneService {

    FortuneAvailabilityResponse getTodayAvailability(String userUuidValue);

    FortuneCreateResponse createFortune(String userUuidValue, FortuneCreateRequest request);
}
