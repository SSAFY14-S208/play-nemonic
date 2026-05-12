package com.nemonicworld.inquiry.service;

import com.nemonicworld.common.exception.EmailDeliveryException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JavaMailInquiryMailSender implements InquiryMailSender {

    private static final String EMAIL_DELIVERY_FAILED_MESSAGE = "이메일 발송에 실패했습니다.";
    private static final String EMAIL_CONFIGURATION_INCOMPLETE_MESSAGE = "이메일 발송 설정이 완료되지 않았습니다.";

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final MailProperties mailProperties;
    private final String from;

    public JavaMailInquiryMailSender(ObjectProvider<JavaMailSender> javaMailSenderProvider,
        MailProperties mailProperties, @Value("${nemonic.inquiry.mail.from:}") String from) {
        this.javaMailSenderProvider = javaMailSenderProvider;
        this.mailProperties = mailProperties;
        this.from = from;
    }

    @Override
    public void sendReply(String to, String subject, String message) {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        String resolvedFrom = resolveFrom();
        if (javaMailSender == null || !StringUtils.hasText(mailProperties.getHost())
            || !StringUtils.hasText(resolvedFrom)) {
            throw new EmailDeliveryException(EMAIL_CONFIGURATION_INCOMPLETE_MESSAGE);
        }

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(resolvedFrom);
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(message);

        try {
            javaMailSender.send(mailMessage);
        } catch (MailException e) {
            throw new EmailDeliveryException(EMAIL_DELIVERY_FAILED_MESSAGE, e);
        }
    }

    private String resolveFrom() {
        if (StringUtils.hasText(from)) {
            return from.trim();
        }

        return mailProperties.getUsername();
    }
}
