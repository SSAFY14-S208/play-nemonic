package com.nemonicworld.gallery.phone.service;

import com.nemonicworld.gallery.phone.dto.request.PhoneDrawingSaveRequest;
import com.nemonicworld.gallery.phone.dto.response.PhoneDrawingSaveResponse;
import com.nemonicworld.gallery.phone.service.drawing.PhoneDrawingSaveUseCase;
import org.springframework.stereotype.Service;

@Service
public class PhoneDrawingServiceImpl implements PhoneDrawingService {

    private final PhoneDrawingSaveUseCase phoneDrawingSaveUseCase;

    public PhoneDrawingServiceImpl(PhoneDrawingSaveUseCase phoneDrawingSaveUseCase) {
        this.phoneDrawingSaveUseCase = phoneDrawingSaveUseCase;
    }

    @Override
    public PhoneDrawingSaveResponse savePhoneDrawing(String userUuidValue, PhoneDrawingSaveRequest request) {
        return phoneDrawingSaveUseCase.savePhoneDrawing(userUuidValue, request);
    }
}
