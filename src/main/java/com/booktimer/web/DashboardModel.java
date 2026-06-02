package com.booktimer.web;

import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerService;
import com.booktimer.user.User;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.Optional;

/**
 * 대시보드 라이브 영역(잔여시간 + 측정 상태)에 필요한 모델 속성을 채우는 공용 빌더.
 *
 * <p>전체 페이지를 그리는 {@link DashboardController}와, htmx 무리로드로 라이브 영역만
 * 다시 그리는 {@link ReadingSessionController}가 <b>같은 상태</b>를 만들도록 한 곳에 모았다.
 * 접속/액션 시점에 {@link ReadingTimerService#accrueToToday(User)}로 누적을 따라잡는다(N-001, N-012).
 */
@Component
public class DashboardModel {

    private final ReadingTimerService timerService;
    private final ReadingSessionRepository sessionRepository;

    public DashboardModel(ReadingTimerService timerService,
                          ReadingSessionRepository sessionRepository) {
        this.timerService = timerService;
        this.sessionRepository = sessionRepository;
    }

    /** 라이브 영역 렌더에 필요한 속성(nickname, remainingSeconds, hasActiveSession, activeStartedAt)을 채운다. */
    public void populate(Model model, User user) {
        ReadingTimer timer = timerService.accrueToToday(user);
        Optional<ReadingSession> activeSession = sessionRepository.findByUserAndEndedAtIsNull(user);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("remainingSeconds", timer.getRemainingSeconds());
        model.addAttribute("atCap", timer.isAtCap());
        model.addAttribute("hasActiveSession", activeSession.isPresent());
        model.addAttribute("activeStartedAt", activeSession.map(ReadingSession::getStartedAt).orElse(null));
    }
}
