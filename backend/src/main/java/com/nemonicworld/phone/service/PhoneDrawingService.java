package com.nemonicworld.phone.service;

import com.nemonicworld.phone.dto.request.PhoneDrawingSaveRequest;
import com.nemonicworld.phone.dto.response.PhoneDrawingSaveResponse;

public interface PhoneDrawingService {

    PhoneDrawingSaveResponse savePhoneDrawing(String userUuidValue, PhoneDrawingSaveRequest request);
}
