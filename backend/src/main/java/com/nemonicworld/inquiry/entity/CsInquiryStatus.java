package com.nemonicworld.inquiry.entity;

import com.nemonicworld.common.exception.BadRequestException;

public enum CsInquiryStatus {
    NEW("new"), IN_PROGRESS("in_progress"), RESOLVED("resolved"), CLOSED("closed");

    private static final String UNSUPPORTED_STATUS_MESSAGE = "문의 상태가 올바르지 않습니다.";

    private final String value;

    CsInquiryStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CsInquiryStatus fromValue(String value) {
        for (CsInquiryStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }

        throw new BadRequestException(UNSUPPORTED_STATUS_MESSAGE);
    }
}
