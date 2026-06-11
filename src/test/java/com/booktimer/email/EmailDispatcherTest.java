package com.booktimer.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * EmailDispatcher 단위 테스트 — EmailSender 위임 + 발송 실패 격리(비동기 스레드라 호출자에 전파 금지).
 *
 * <p>(@Async는 Spring 프록시에서만 적용되므로 직접 생성한 단위 테스트에선 동기 실행 — 위임·격리 로직만 검증.)
 */
@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

    @Mock
    EmailSender emailSender;

    @Test
    void dispatch_delegatesToEmailSender() {
        new EmailDispatcher(emailSender).dispatch("u@example.com", "제목", "<p>본문</p>");

        verify(emailSender).send("u@example.com", "제목", "<p>본문</p>");
    }

    @Test
    void dispatch_isolatesSendFailure() {
        doThrow(new EmailSendException("smtp down", new RuntimeException()))
                .when(emailSender).send(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // 발송 실패가 호출자에게 전파되지 않아야 한다(비동기라 흐름과 분리 — 격리·로그).
        assertThatCode(() -> new EmailDispatcher(emailSender)
                .dispatch("u@example.com", "제목", "<p>본문</p>"))
                .doesNotThrowAnyException();
    }
}
