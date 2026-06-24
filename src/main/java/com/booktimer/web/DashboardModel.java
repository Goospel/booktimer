package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.WeeklyDebt;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 대시보드 라이브 영역(부채 + 측정 상태)에 필요한 모델 속성을 채우는 공용 빌더.
 *
 * <p>전체 페이지를 그리는 {@link DashboardController}와, htmx 무리로드로 라이브 영역만
 * 다시 그리는 {@link ReadingSessionController}가 <b>같은 상태</b>를 만들도록 한 곳에 모았다.
 * 부채는 저장된 단일 카운터가 아니라 완료 세션에서 유도하므로(7일 윈도우 per-day,
 * {@link ReadingDebtService}) 접속/액션 시점에 그때그때 계산한다(옛 Lazy accrual 불필요).
 */
@Component
public class DashboardModel {

    private final ReadingDebtService debtService;
    private final ReadingSessionRepository sessionRepository;
    private final BookRepository bookRepository;
    private final ReadingTimerRepository timerRepository;

    public DashboardModel(ReadingDebtService debtService,
                          ReadingSessionRepository sessionRepository,
                          BookRepository bookRepository,
                          ReadingTimerRepository timerRepository) {
        this.debtService = debtService;
        this.sessionRepository = sessionRepository;
        this.bookRepository = bookRepository;
        this.timerRepository = timerRepository;
    }

    /**
     * 대시보드 라이브 영역의 순수 계산 결과 — SSR({@link #populate})과 REST API({@code DashboardApiController})가
     * 함께 쓰는 단일 출처. 엔티티를 직접 노출하므로 API는 별도 DTO로 매핑해 직렬화한다.
     */
    public record LiveState(
            String nickname,
            String loginId,
            long remainingSeconds,
            long carriedDebtSeconds,
            long todayGoalSeconds,
            boolean hasActiveSession,
            Instant activeStartedAt,
            String activeBookTitle,
            long activeBookTotalSeconds,
            List<Book> readingBooks,
            List<Book> finishedBooks,
            Long recentBookId
    ) {}

    /**
     * 라이브 영역 상태를 순수하게 계산해 {@link LiveState}로 반환한다.
     *
     * <p>{@code carriedDebtSeconds}(floor)는 음수가 불가능하지만 방어적으로 {@code max(0, ...)} 처리한다.
     * SSR·API 양쪽이 이 메서드를 통해 동일 상태를 만들어 패리티가 보장된다.
     */
    public LiveState computeLive(User user) {
        WeeklyDebt debt = debtService.weeklyDebt(user);
        boolean carryover = timerRepository.findByUser(user)
                .map(ReadingTimer::isDebtCarryover)
                .orElse(true);
        long carriedDebtSeconds = carryover
                ? Math.max(0L, debt.totalDebtSeconds() - debt.todayDebtSeconds())
                : 0L;
        long headlineSeconds = carryover ? debt.totalDebtSeconds() : debt.todayDebtSeconds();

        Optional<ReadingSession> activeSession = sessionRepository.findActiveWithBook(user);
        Book activeBook = activeSession.map(ReadingSession::getBook).orElse(null);

        List<Book> books = bookRepository.findByUserOrderByCreatedAtDesc(user);
        List<Book> readingBooks = books.stream().filter(b -> b.getStatus() == BookStatus.READING).toList();
        List<Book> finishedBooks = books.stream().filter(b -> b.getStatus() == BookStatus.FINISHED).toList();

        List<Long> recent = sessionRepository.findRecentlyReadBookIds(user, PageRequest.of(0, 1));

        return new LiveState(
                user.getNickname(),
                user.getLoginId(),
                headlineSeconds,
                carriedDebtSeconds,
                debtService.todayGoalSeconds(user),
                activeSession.isPresent(),
                activeSession.map(ReadingSession::getStartedAt).orElse(null),
                activeBook != null ? activeBook.getTitle() : null,
                activeBook != null ? sessionRepository.sumDurationByUserAndBook(user, activeBook) : 0L,
                readingBooks,
                finishedBooks,
                recent.isEmpty() ? null : recent.get(0)
        );
    }

    /**
     * 라이브 영역 렌더 속성을 채운다 — 부채 상태(nickname, remainingSeconds=오늘 부채), 측정 상태
     * (hasActiveSession), 측정 중인 책(activeBookTitle)과 그 책의 누적 독서 시간(activeBookTotalSeconds),
     * 시작 시 고를 책 목록(readingBooks/finishedBooks)과 최근 읽은 책(recentBookId).
     *
     * <p><b>{@code remainingSeconds}는 헤드라인 카운트다운(JS {@code data-remaining})의 시작값</b>이고,
     * 밀린 부채 합산 표시 토글({@link ReadingTimer#isDebtCarryover()})에 따라 의미가 갈린다 — 켜지면
     * "오늘 부채 + 윈도우 내 빠뜨린 날 합"(어제까지 밀린 빚 포함, {@link WeeklyDebt#totalDebtSeconds()}),
     * 꺼지면 "오늘 부채"만(목표 − 오늘 읽은 양). 속성명은 옛 이름을 유지해 템플릿·JS가 그대로 동작한다.
     *
     * <p>{@code carriedDebtSeconds}는 <b>floor</b>(JS {@code data-floor})다 — 윈도우 내 빠뜨린 날 부채 합으로,
     * 측정 카운트다운이 이 값에서 멈춘다(어제 빚은 오늘 읽어서는 못 갚으니까 — 날짜별 독립 모델과 일관).
     * 합산 OFF면 0이라 측정은 0까지 줄어 현행과 동일하다. "이번 주 빠뜨린 날" 목록 자체는
     * 독서 기록 화면({@code /history}, {@link com.booktimer.web.HistoryController})에 있다.
     *
     * <p>측정 대상은 "읽는 중"·"완독"인 책뿐이다 — "읽고싶음"은 아직 펴지 않은 책이라 시간을 재는 게
     * 이상하므로 드롭다운에서 제외한다(optgroup으로 「읽는 중」/「완독」을 시각적으로 구분). 가장 최근에
     * 측정한 책(recentBookId)을 미리 선택해 "이어 읽기"를 자연스럽게 한다(없으면 브라우저 기본=첫 옵션).
     *
     * <p>{@code activeStartedAt}은 화면에 시각 자체를 노출하진 않지만(사용자에겐 타임존이 보일 필요 없음),
     * 타이머 카드의 경과 계산(JS {@code data-started})에 여전히 필요하므로 모델에 남긴다.
     */
    public void populate(Model model, User user) {
        LiveState live = computeLive(user);
        model.addAttribute("nickname", live.nickname());
        model.addAttribute("loginId", live.loginId());
        model.addAttribute("remainingSeconds", live.remainingSeconds());
        model.addAttribute("carriedDebtSeconds", live.carriedDebtSeconds());
        model.addAttribute("hasActiveSession", live.hasActiveSession());
        model.addAttribute("activeStartedAt", live.activeStartedAt());
        model.addAttribute("activeBookTitle", live.activeBookTitle());
        model.addAttribute("activeBookTotalSeconds", live.activeBookTotalSeconds());
        model.addAttribute("readingBooks", live.readingBooks());
        model.addAttribute("finishedBooks", live.finishedBooks());
        model.addAttribute("recentBookId", live.recentBookId());
    }
}
