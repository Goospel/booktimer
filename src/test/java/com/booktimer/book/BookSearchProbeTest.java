package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 책 검색 감시 판정(REQ-389) — 순수 단위. 스케줄러 · 스프링 없이 {@code check()}를 직접 부른다.
 *
 * <p>가짜 클라이언트는 {@link BookSearchProbe#PROBE_ISBN}에만 책을 돌려준다 — 다른 ISBN을 물으면 0건이라
 * 「성공」 테스트가 실패로 바뀐다(감시가 엉뚱한 책을 묻는 회귀를 함께 잡는다).
 */
class BookSearchProbeTest {

    private static final BookSearchResult DEMIAN = new BookSearchResult(
            "데미안", "헤르만 헤세", BookSearchProbe.PROBE_ISBN, null, "민음사", null, null, "2000-12-20");

    private final BookSearchClient client = mock(BookSearchClient.class);
    private final BookSearchProbe probe = new BookSearchProbe(client);

    // 스텁을 바꿔 끼우므로 doReturn/doThrow 꼴 — when(...)은 앞 스텁(던지기)을 실제로 불러 버린다.
    private void found() {
        when(client.isEnabled()).thenReturn(true);
        doReturn(Optional.empty()).when(client).lookupByIsbn(anyString());
        doReturn(Optional.of(DEMIAN)).when(client).lookupByIsbn(BookSearchProbe.PROBE_ISBN);
    }

    private void notFound() {
        when(client.isEnabled()).thenReturn(true);
        doReturn(Optional.empty()).when(client).lookupByIsbn(anyString());
    }

    private void unavailable() {
        when(client.isEnabled()).thenReturn(true);
        doThrow(new BookSearchUnavailableException("http=500")).when(client).lookupByIsbn(anyString());
    }

    @Test
    @DisplayName("REQ-389 · 첫 점검 전은 UP이다")
    void upBeforeFirstCheck() {
        assertThat(probe.isDown()).isFalse();
    }

    @Test
    @DisplayName("REQ-389 · 한 번 실패로는 DOWN이 아니다")
    void oneFailureIsNotDown() {
        unavailable();
        probe.check();
        assertThat(probe.isDown()).isFalse();
    }

    @Test
    @DisplayName("REQ-389 · 두 번 잇달아 실패면 DOWN이다(사이에 성공이 끼면 다시 센다)")
    void twoConsecutiveFailuresAreDown() {
        unavailable();
        probe.check();
        found();
        probe.check();
        unavailable();
        probe.check();
        assertThat(probe.isDown()).as("실패 · 성공 · 실패는 잇달은 두 번이 아니다").isFalse();

        probe.check();
        assertThat(probe.isDown()).isTrue();
    }

    @Test
    @DisplayName("REQ-389 · 알려진 책이 0건이면 실패로 센다(「200 + 빈 결과」가 알라딘 종료의 꼴)")
    void zeroResultsCountAsFailure() {
        notFound();
        probe.check();
        probe.check();
        assertThat(probe.isDown()).isTrue();
    }

    @Test
    @DisplayName("REQ-389 · 검색 장애 예외도 실패로 센다(예외가 스케줄러 밖으로 새지 않는다)")
    void unavailableExceptionCountsAsFailure() {
        unavailable();
        probe.check();
        probe.check();
        assertThat(probe.isDown()).isTrue();
    }

    @Test
    @DisplayName("REQ-389 · 키가 없으면 바로 DOWN이다(외부 호출 없음)")
    void noKeyIsDownImmediately() {
        when(client.isEnabled()).thenReturn(false);
        probe.check();
        assertThat(probe.isDown()).isTrue();
        verify(client, never()).lookupByIsbn(anyString());
    }

    @Test
    @DisplayName("REQ-389 · 성공 한 번이면 다시 UP이다")
    void oneSuccessRecovers() {
        unavailable();
        probe.check();
        probe.check();
        assertThat(probe.isDown()).isTrue();

        found();
        probe.check();
        assertThat(probe.isDown()).isFalse();

        unavailable();
        probe.check();
        assertThat(probe.isDown()).as("회복 뒤 실패 수는 0부터 다시 센다").isFalse();
    }

    @Test
    @DisplayName("REQ-389 · 10분마다 돌고 첫 회는 기동 1분 뒤다")
    void runsEveryTenMinutes() throws NoSuchMethodException {
        Scheduled s = BookSearchProbe.class.getMethod("check").getAnnotation(Scheduled.class);
        assertThat(s.fixedDelay()).isEqualTo(600_000L);
        assertThat(s.initialDelayString()).endsWith(":60000}");
    }
}
