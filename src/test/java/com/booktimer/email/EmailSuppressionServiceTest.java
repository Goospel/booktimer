package com.booktimer.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 억제(suppression) 서비스 로직 단위 테스트 — 이메일 정규화·멱등·Clock 기록을 리포지토리 mock으로 못 박는다.
 * 실제 H2 제약·쿼리는 {@link EmailSuppressionRepositoryTest}가 커버(역할 분담).
 */
@ExtendWith(MockitoExtension.class)
class EmailSuppressionServiceTest {

    private static final Instant FIXED = Instant.parse("2026-07-08T00:00:00Z");

    @Mock
    EmailSuppressionRepository repository;

    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

    EmailSuppressionService service;

    EmailSuppressionService newService() {
        return new EmailSuppressionService(repository, clock);
    }

    @Test
    void suppress_new_savesNormalizedLowercasedWithClockInstant() {
        service = newService();
        when(repository.existsByEmail("foo@example.com")).thenReturn(false);

        service.suppress("  Foo@Example.COM ", SuppressionReason.BOUNCE, "Permanent/General");

        ArgumentCaptor<EmailSuppression> captor = ArgumentCaptor.forClass(EmailSuppression.class);
        verify(repository).save(captor.capture());
        EmailSuppression saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("foo@example.com"); // trim + lowercase
        assertThat(saved.getReason()).isEqualTo(SuppressionReason.BOUNCE);
        assertThat(saved.getDetail()).isEqualTo("Permanent/General");
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED);
    }

    @Test
    void suppress_alreadySuppressed_isIdempotentNoSecondInsert() {
        service = newService();
        when(repository.existsByEmail("dup@example.com")).thenReturn(true);

        service.suppress("dup@example.com", SuppressionReason.COMPLAINT, null);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void suppress_blankOrNullEmail_noSave() {
        service = newService();

        service.suppress("   ", SuppressionReason.BOUNCE, null);
        service.suppress(null, SuppressionReason.BOUNCE, null);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void isSuppressed_normalizesBeforeLookup() {
        service = newService();
        when(repository.existsByEmail("check@example.com")).thenReturn(true);

        assertThat(service.isSuppressed("  Check@Example.com ")).isTrue();
        assertThat(service.isSuppressed(null)).isFalse();
    }
}
