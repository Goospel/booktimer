package com.booktimer.study;

import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 선점의 경합 창 — 첫 UPDATE가 「행 없음」으로 0을 받은 <b>직후</b> 다른 요청이 행을 만든 순간(T-261).
 *
 * <p>그 0은 「몫 소진」이 아니라 「그때 행이 없었다」다. 행이 이제 있다고 곧바로 소진으로 판정하면
 * 몫이 남았는데 거절한다 — 그날 첫 요청이 동시에 몰리면 운영(MySQL)에서도 난다. 통합 테스트
 * {@code StudyAiUsageServiceTest#concurrentConsume_lettsExactlyMaxThrough}가 약 1000회에 1번 이 창에
 * 걸려 CI를 간헐로 깨뜨렸다. 여기서는 목으로 그 순간을 <b>결정적으로</b> 만든다.
 */
class StudyAiUsageRaceWindowTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 3);

    private final StudyAiUsageRepository usageRepository = mock(StudyAiUsageRepository.class);
    private final StudyAiDailyTotalRepository dailyTotalRepository = mock(StudyAiDailyTotalRepository.class);
    private final StudyAiUsageService service =
            new StudyAiUsageService(usageRepository, dailyTotalRepository, 50);

    @Test
    @DisplayName("사용자 몫: 첫 UPDATE 뒤에 남이 행을 만들었으면 소진이 아니다 — 다시 UPDATE해 선점한다")
    void userQuota_rowAppearedAfterFirstUpdate_isNotExhaustion() {
        User user = mock(User.class);
        when(usageRepository.consume(user, DAY, Kind.PLAN, Kind.PLAN.max())).thenReturn(0, 1);
        when(usageRepository.existsByUserAndUsageDateAndKind(user, DAY, Kind.PLAN)).thenReturn(true);

        assertThat(service.tryConsume(user, DAY, Kind.PLAN)).isTrue();
        verify(usageRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자 몫: INSERT 경합에 져도 소진이 아니다 — 이긴 쪽 행에서 다시 UPDATE해 선점한다")
    void userQuota_lostInsertRace_isNotExhaustion() {
        User user = mock(User.class);
        when(usageRepository.consume(user, DAY, Kind.PLAN, Kind.PLAN.max())).thenReturn(0, 1);
        when(usageRepository.existsByUserAndUsageDateAndKind(user, DAY, Kind.PLAN)).thenReturn(false);
        when(usageRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThat(service.tryConsume(user, DAY, Kind.PLAN)).isTrue();
    }

    @Test
    @DisplayName("전역 몫: 첫 UPDATE 뒤에 남이 행을 만들었으면 소진이 아니다 — 다시 UPDATE해 선점한다")
    void globalQuota_rowAppearedAfterFirstUpdate_isNotExhaustion() {
        when(dailyTotalRepository.consume(DAY, 50)).thenReturn(0, 1);
        when(dailyTotalRepository.existsByUsageDate(DAY)).thenReturn(true);

        assertThat(service.tryConsumeGlobal(Instant.parse("2026-09-03T01:00:00Z"))).isTrue();
        verify(dailyTotalRepository, never()).save(any());
    }
}
