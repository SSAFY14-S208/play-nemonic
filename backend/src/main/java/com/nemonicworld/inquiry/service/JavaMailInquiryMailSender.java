package com.nemonicworld.inquiry.service;

import com.nemonicworld.common.exception.EmailDeliveryException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JavaMailInquiryMailSender implements InquiryMailSender {

    private static final String EMAIL_DELIVERY_FAILED_MESSAGE = "이메일 발송에 실패했습니다.";

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final String from;

    public JavaMailInquiryMailSender(ObjectProvider<JavaMailSender> javaMailSenderProvider,
        @Value("${nemonic.inquiry.mail.from:}") String from) {
        this.javaMailSenderProvider = javaMailSenderProvider;
        this.from = from;
    }

    @Override
    public void sendReply(String to, String subject, String message) {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null || !StringUtils.hasText(from)) {
            throw new EmailDeliveryException(EMAIL_DELIVERY_FAILED_MESSAGE);
        }

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(from);
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(message);

        try {
            javaMailSender.send(mailMessage);
        } catch (MailException e) {
            throw new EmailDeliveryException(EMAIL_DELIVERY_FAILED_MESSAGE, e);
        }
    }
}
