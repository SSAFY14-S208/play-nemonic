package com.nemonicworld.inquiry.service;

import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.inquiry.dto.request.CsInquiryCreateRequest;
import com.nemonicworld.inquiry.dto.request.CsInquiryReplyRequest;
import com.nemonicworld.inquiry.dto.request.CsInquiryStatusUpdateRequest;
import com.nemonicworld.inquiry.dto.response.CsInquiryCreateResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryDetailResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryListResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryReplyResponse;
import com.nemonicworld.inquiry.dto.response.CsInquiryStatusUpdateResponse;

public interface CsInquiryService {

    CsInquiryCreateResponse createInquiry(String userUuid, String userAgent, String referer,
        CsInquiryCreateRequest request);

    CsInquiryListResponse getInquiries(AdminPrincipal adminPrincipal, String status, String type, String keyword,
        String userUuid, String page, String size);

    CsInquiryDetailResponse getInquiry(AdminPrincipal adminPrincipal, String inquiryId);

    CsInquiryReplyResponse replyInquiry(AdminPrincipal adminPrincipal, String inquiryId, CsInquiryReplyRequest request,
        AdminClientInfo clientInfo);

    CsInquiryStatusUpdateResponse updateInquiryStatus(AdminPrincipal adminPrincipal, String inquiryId,
        CsInquiryStatusUpdateRequest request, AdminClientInfo clientInfo);
}
