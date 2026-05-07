package com.nemonicworld.inquiry.service;

import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;

public interface CsInquiryService {

    CsInquiryCreateResponse createInquiry(String userUuid, String userAgent, String referer,
        CsInquiryCreateRequest request);
}
