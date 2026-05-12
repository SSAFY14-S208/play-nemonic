package com.nemonicworld.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.EmailDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class JavaMailInquiryMailSenderTest {

    private static final String CONFIGURATION_INCOMPLETE_MESSAGE = "이메일 발송 설정이 완료되지 않았습니다.";
    private static final String DELIVERY_FAILED_MESSAGE = "이메일 발송에 실패했습니다.";

    @Mock
    private ObjectProvider<JavaMailSender> javaMailSenderProvider;

    @Mock
    private JavaMailSender javaMailSender;

    private MailProperties mailProperties;

    @BeforeEach
    void setUp() {
        mailProperties = new MailProperties();
        mailProperties.setHost("smtp.example.com");
        mailProperties.setUsername("smtp-user@example.com");
    }

    @Test
    void sendReplyUsesConfiguredFromAddress() {
        givenMailSenderAvailable();
        JavaMailInquiryMailSender inquiryMailSender = new JavaMailInquiryMailSender(javaMailSenderProvider,
            mailProperties, " support@example.com ");

        inquiryMailSender.sendReply("user@example.com", "답변드립니다", "문의 내용을 확인했습니다.");

        ArgumentCaptor<SimpleMailMessage> mailMessageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(mailMessageCaptor.capture());
        SimpleMailMessage mailMessage = mailMessageCaptor.getValue();
        assertThat(mailMessage.getFrom()).isEqualTo("support@example.com");
        assertThat(mailMessage.getTo()).containsExactly("user@example.com");
        assertThat(mailMessage.getSubject()).isEqualTo("답변드립니다");
        assertThat(mailMessage.getText()).isEqualTo("문의 내용을 확인했습니다.");
    }

    @Test
    void sendReplyUsesMailUsernameWhenFromAddressIsBlank() {
        givenMailSenderAvailable();
        JavaMailInquiryMailSender inquiryMailSender = new JavaMailInquiryMailSender(javaMailSenderProvider,
            mailProperties, " ");

        inquiryMailSender.sendReply("user@example.com", "답변드립니다", "문의 내용을 확인했습니다.");

        ArgumentCaptor<SimpleMailMessage> mailMessageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(mailMessageCaptor.capture());
        assertThat(mailMessageCaptor.getValue().getFrom()).isEqualTo("smtp-user@example.com");
    }

    @Test
    void sendReplyRejectsMissingMailHostBeforeSending() {
        givenMailSenderAvailable();
        mailProperties.setHost("");
        JavaMailInquiryMailSender inquiryMailSender = new JavaMailInquiryMailSender(javaMailSenderProvider,
            mailProperties, "support@example.com");

        assertThatThrownBy(() -> inquiryMailSender.sendReply("user@example.com", "답변드립니다", "문의 내용을 확인했습니다."))
            .isInstanceOf(EmailDeliveryException.class).hasMessage(CONFIGURATION_INCOMPLETE_MESSAGE);

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendReplyRejectsMissingMailSenderBeforeSending() {
        given(javaMailSenderProvider.getIfAvailable()).willReturn(null);
        JavaMailInquiryMailSender inquiryMailSender = new JavaMailInquiryMailSender(javaMailSenderProvider,
            mailProperties, "support@example.com");

        assertThatThrownBy(() -> inquiryMailSender.sendReply("user@example.com", "답변드립니다", "문의 내용을 확인했습니다."))
            .isInstanceOf(EmailDeliveryException.class).hasMessage(CONFIGURATION_INCOMPLETE_MESSAGE);
    }

    @Test
    void sendReplyWrapsMailException() {
        givenMailSenderAvailable();
        MailSendException cause = new MailSendException("smtp failed");
        doThrow(cause).when(javaMailSender).send(any(SimpleMailMessage.class));
        JavaMailInquiryMailSender inquiryMailSender = new JavaMailInquiryMailSender(javaMailSenderProvider,
            mailProperties, "support@example.com");

        assertThatThrownBy(() -> inquiryMailSender.sendReply("user@example.com", "답변드립니다", "문의 내용을 확인했습니다."))
            .isInstanceOf(EmailDeliveryException.class).hasMessage(DELIVERY_FAILED_MESSAGE).hasCause(cause);
    }

    private void givenMailSenderAvailable() {
        given(javaMailSenderProvider.getIfAvailable()).willReturn(javaMailSender);
    }
}
