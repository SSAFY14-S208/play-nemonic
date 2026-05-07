package com.nemonicworld.inquiry.entity;

import com.nemonicworld.common.exception.BadRequestException;

public enum CsInquiryType {
    ERROR("error"), FEATURE_REQUEST("feature_request"), CONTENT_REPORT("content_report"), OTHER("other");

    private static final String UNSUPPORTED_TYPE_MESSAGE = "지원하지 않는 문의 유형입니다.";

    private final String value;

    CsInquiryType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CsInquiryType fromValue(String value) {
        for (CsInquiryType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        throw new BadRequestException(UNSUPPORTED_TYPE_MESSAGE);
    }
}
