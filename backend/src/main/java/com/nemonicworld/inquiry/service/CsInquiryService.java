package com.nemonicworld.inquiry.service;

import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryDetailResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListResponse;

public interface CsInquiryService {

    CsInquiryCreateResponse createInquiry(String userUuid, String userAgent, String referer,
        CsInquiryCreateRequest request);

    CsInquiryListResponse getInquiries(AdminPrincipal adminPrincipal, String status, String type, String keyword,
        String userUuid, String page, String size);

    CsInquiryDetailResponse getInquiry(AdminPrincipal adminPrincipal, String inquiryId);
}
