package com.booktimer.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SmtpEmailSender}의 진짜 로직 검증 — JavaMailSender 위임과 실패 격리(EmailSendException 래핑).
 *
 * <p>발송 위임·예외 래핑은 어댑터 자체 코드라 단위로 못 박는다(어느 어댑터가 빈으로 선택되는가는
 * @ConditionalOnProperty = 프레임워크 동작이라 별도 검증하지 않음). 네트워크 없이 mock으로 격리.
 */
@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    JavaMailSender mailSender;

    @Test
    void send_buildsMessageAndDelegatesToMailSender() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "noreply@booktimer.app");

        sender.send("user@example.com", "제목", "<p>본문</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("제목");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(sent.getFrom()[0].toString()).isEqualTo("noreply@booktimer.app");
    }

    @Test
    void send_wrapsFailureInEmailSendException() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "noreply@booktimer.app");

        assertThatThrownBy(() -> sender.send("user@example.com", "제목", "<p>본문</p>"))
                .isInstanceOf(EmailSendException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
