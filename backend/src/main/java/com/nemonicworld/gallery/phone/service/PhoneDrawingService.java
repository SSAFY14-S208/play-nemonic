package com.nemonicworld.gallery.phone.service;

import com.nemonicworld.gallery.phone.dto.request.PhoneDrawingSaveRequest;
import com.nemonicworld.gallery.phone.dto.response.PhoneDrawingSaveResponse;

public interface PhoneDrawingService {

    PhoneDrawingSaveResponse savePhoneDrawing(String userUuidValue, PhoneDrawingSaveRequest request);
}
