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
import com.nemonicworld.inquiry.service.admin.AdminInquiryCommandUseCase;
import com.nemonicworld.inquiry.service.admin.AdminInquiryQueryUseCase;
import com.nemonicworld.inquiry.service.inquiry.CsInquiryCreateUseCase;
import org.springframework.stereotype.Service;

@Service
public class CsInquiryServiceImpl implements CsInquiryService {

    private final CsInquiryCreateUseCase csInquiryCreateUseCase;
    private final AdminInquiryQueryUseCase adminInquiryQueryUseCase;
    private final AdminInquiryCommandUseCase adminInquiryCommandUseCase;

    public CsInquiryServiceImpl(CsInquiryCreateUseCase csInquiryCreateUseCase,
        AdminInquiryQueryUseCase adminInquiryQueryUseCase, AdminInquiryCommandUseCase adminInquiryCommandUseCase) {
        this.csInquiryCreateUseCase = csInquiryCreateUseCase;
        this.adminInquiryQueryUseCase = adminInquiryQueryUseCase;
        this.adminInquiryCommandUseCase = adminInquiryCommandUseCase;
    }

    @Override
    public CsInquiryCreateResponse createInquiry(String userUuid, String userAgent, String referer,
        CsInquiryCreateRequest request) {
        return csInquiryCreateUseCase.createInquiry(userUuid, userAgent, referer, request);
    }

    @Override
    public CsInquiryListResponse getInquiries(AdminPrincipal adminPrincipal, String status, String type, String keyword,
        String userUuid, String pageValue, String sizeValue) {
        return adminInquiryQueryUseCase.getInquiries(adminPrincipal, status, type, keyword, userUuid, pageValue,
            sizeValue);
    }

    @Override
    public CsInquiryDetailResponse getInquiry(AdminPrincipal adminPrincipal, String inquiryIdValue) {
        return adminInquiryQueryUseCase.getInquiry(adminPrincipal, inquiryIdValue);
    }

    @Override
    public CsInquiryReplyResponse replyInquiry(AdminPrincipal adminPrincipal, String inquiryIdValue,
        CsInquiryReplyRequest request, AdminClientInfo clientInfo) {
        return adminInquiryCommandUseCase.replyInquiry(adminPrincipal, inquiryIdValue, request, clientInfo);
    }

    @Override
    public CsInquiryStatusUpdateResponse updateInquiryStatus(AdminPrincipal adminPrincipal, String inquiryIdValue,
        CsInquiryStatusUpdateRequest request, AdminClientInfo clientInfo) {
        return adminInquiryCommandUseCase.updateInquiryStatus(adminPrincipal, inquiryIdValue, request, clientInfo);
    }
}
