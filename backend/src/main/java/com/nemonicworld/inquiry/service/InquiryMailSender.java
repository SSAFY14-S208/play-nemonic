package com.nemonicworld.inquiry.service;

public interface InquiryMailSender {

    void sendReply(String to, String subject, String message);
}
