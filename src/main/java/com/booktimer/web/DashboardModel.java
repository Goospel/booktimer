package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
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
    private final BookRepository bookRepository;

    public DashboardModel(ReadingTimerService timerService,
                          ReadingSessionRepository sessionRepository,
                          BookRepository bookRepository) {
        this.timerService = timerService;
        this.sessionRepository = sessionRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * 라이브 영역 렌더 속성을 채운다 — 타이머 상태(nickname, remainingSeconds, atCap), 측정 상태
     * (hasActiveSession), 측정 중인 책(activeBookTitle)과 그 책의 누적 독서 시간(activeBookTotalSeconds),
     * 시작 시 고를 책 목록(books).
     *
     * <p>{@code activeStartedAt}은 화면에 시각 자체를 노출하진 않지만(사용자에겐 타임존이 보일 필요 없음),
     * 타이머 카드의 경과 계산(JS {@code data-started})에 여전히 필요하므로 모델에 남긴다.
     */
    public void populate(Model model, User user) {
        ReadingTimer timer = timerService.accrueToToday(user);
        Optional<ReadingSession> activeSession = sessionRepository.findActiveWithBook(user);
        Book activeBook = activeSession.map(ReadingSession::getBook).orElse(null);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("remainingSeconds", timer.getRemainingSeconds());
        model.addAttribute("atCap", timer.isAtCap());
        model.addAttribute("hasActiveSession", activeSession.isPresent());
        model.addAttribute("activeStartedAt", activeSession.map(ReadingSession::getStartedAt).orElse(null));
        model.addAttribute("activeBookTitle", activeBook != null ? activeBook.getTitle() : null);
        // 측정 중인 책의 누적 독서 시간(완료 세션 합). 책 미지정 측정이면 0.
        model.addAttribute("activeBookTotalSeconds",
                activeBook != null ? sessionRepository.sumDurationByUserAndBook(user, activeBook) : 0L);
        model.addAttribute("books", bookRepository.findByUserOrderByCreatedAtDesc(user));
    }
}
