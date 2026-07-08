package com.booktimer.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 억제 데코레이터 단위 테스트 — 억제된 주소는 위임(delegate)이 절대 호출되지 않고, 정상 주소는 그대로 위임된다.
 * 두 발송 경로(트랜잭션·넛지)가 공유하는 단일 게이트라 이 불변식이 핵심.
 */
@ExtendWith(MockitoExtension.class)
class SuppressionAwareEmailSenderTest {

    @Mock
    EmailSender delegate;

    @Mock
    EmailSuppressionService suppressionService;

    @Test
    void send_notSuppressed_delegatesUnchanged() {
        when(suppressionService.isSuppressed("ok@example.com")).thenReturn(false);

        new SuppressionAwareEmailSender(delegate, suppressionService)
                .send("ok@example.com", "제목", "<p>본문</p>");

        verify(delegate).send("ok@example.com", "제목", "<p>본문</p>");
    }

    @Test
    void send_suppressed_skipsDelegateEntirely() {
        when(suppressionService.isSuppressed("bad@example.com")).thenReturn(true);

        new SuppressionAwareEmailSender(delegate, suppressionService)
                .send("bad@example.com", "제목", "<p>본문</p>");

        verify(delegate, never()).send(anyString(), anyString(), anyString());
    }
}
