package com.nemonicworld.inquiry.entity;

public enum CsInquiryStatus {
    NEW("new");

    private final String value;

    CsInquiryStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
