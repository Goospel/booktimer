package com.booktimer.web;

import com.booktimer.book.BookRepository;
import com.booktimer.session.DayDebt;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.WeeklyDebt;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DashboardModel 단위 테스트 (Mockito — DB 무관).
 *
 * <p>대시보드 헤드라인이 부채 합산 토글(ReadingTimer.debtCarryover)에 따라 무엇을 싣는지 격리 검증한다.
 * 부채 계산 자체는 {@link com.booktimer.session.WeeklyDebtCalculatorTest}·
 * {@link com.booktimer.session.ReadingDebtServiceTest}가 본다. 여기선 표시 설정 분기만:
 * <ul>
 *   <li><b>토글 ON</b> — 헤드라인(remainingSeconds) = 오늘 부채 + 윈도우 빠뜨린 날 합,
 *       floor(carriedDebtSeconds) = 빠뜨린 날 합(측정으로 안 줄어드는 어제까지의 빚).</li>
 *   <li><b>토글 OFF</b> — 헤드라인 = 오늘 부채만, floor = 0 (현행 동작과 동일).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DashboardModelTest {

    @Mock
    private ReadingDebtService debtService;
    @Mock
    private ReadingSessionRepository sessionRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private ReadingTimerRepository timerRepository;

    private DashboardModel dashboardModel;
    private User user;

    @BeforeEach
    void setUp() {
        dashboardModel = new DashboardModel(debtService, sessionRepository, bookRepository, timerRepository);
        user = User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        // 공통: 진행 중 세션 없음, 책 목록·최근 책 없음(헤드라인 분기와 무관한 부분).
        when(sessionRepository.findActiveWithBook(user)).thenReturn(Optional.empty());
        when(bookRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(sessionRepository.findRecentlyReadBookIds(any(), any())).thenReturn(List.of());
    }

    /** 오늘 부채 70분(4200) + 어제 빚 50분(3000) → 윈도우 미상환 합 7200. 오늘 목표 70분(4200). */
    private WeeklyDebt todayPlusYesterday() {
        return new WeeklyDebt(4200L, 4200L, 0L, List.of(new DayDebt(LocalDate.of(2026, 6, 8), 3000L)));
    }

    private ReadingTimer timerWithCarryover(boolean on) {
        ReadingTimer timer = ReadingTimer.of(4200L);
        timer.updateSettings(4200L, on);
        return timer;
    }

    @Test
    @DisplayName("부채 합산 ON: 헤드라인 = 오늘+빠뜨린 날 합, carriedDebtSeconds = 빠뜨린 날 합")
    void carryoverOn_headlineIsTotal_floorIsCarried() {
        when(debtService.weeklyDebt(user)).thenReturn(todayPlusYesterday());
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timerWithCarryover(true)));

        ExtendedModelMap model = new ExtendedModelMap();
        dashboardModel.populate(model, user);

        assertThat(model.get("remainingSeconds")).isEqualTo(7200L); // 4200 + 3000
        assertThat(model.get("carriedDebtSeconds")).isEqualTo(3000L);
    }

    @Test
    @DisplayName("부채 합산 OFF: 헤드라인 = 오늘 부채만, carriedDebtSeconds = 0 (현행 동작)")
    void carryoverOff_headlineIsTodayOnly_floorZero() {
        when(debtService.weeklyDebt(user)).thenReturn(todayPlusYesterday());
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timerWithCarryover(false)));

        ExtendedModelMap model = new ExtendedModelMap();
        dashboardModel.populate(model, user);

        assertThat(model.get("remainingSeconds")).isEqualTo(4200L); // 오늘 부채만
        assertThat(model.get("carriedDebtSeconds")).isEqualTo(0L);
    }
}
