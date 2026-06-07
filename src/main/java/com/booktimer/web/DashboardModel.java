package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.WeeklyDebt;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

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

    public DashboardModel(ReadingDebtService debtService,
                          ReadingSessionRepository sessionRepository,
                          BookRepository bookRepository) {
        this.debtService = debtService;
        this.sessionRepository = sessionRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * 라이브 영역 렌더 속성을 채운다 — 부채 상태(nickname, remainingSeconds=오늘 부채), 측정 상태
     * (hasActiveSession), 측정 중인 책(activeBookTitle)과 그 책의 누적 독서 시간(activeBookTotalSeconds),
     * 시작 시 고를 책 목록(readingBooks/finishedBooks)과 최근 읽은 책(recentBookId).
     *
     * <p><b>{@code remainingSeconds}는 "오늘 부채"</b>(목표 − 오늘 읽은 양)다 — 헤드라인 카운트다운(JS
     * {@code data-remaining})의 시작값. 속성명은 옛 이름을 유지해 템플릿·JS가 그대로 동작한다.
     * 대시보드는 헤드라인(오늘 부채)만 보여준다 — "이번 주 빠뜨린 날" 목록({@link WeeklyDebt#missedDays()})은
     * 독서 기록 화면({@code /history}, {@link com.booktimer.web.HistoryController})으로 옮겼다.
     *
     * <p>측정 대상은 "읽는 중"·"완독"인 책뿐이다 — "읽고싶음"은 아직 펴지 않은 책이라 시간을 재는 게
     * 이상하므로 드롭다운에서 제외한다(optgroup으로 「읽는 중」/「완독」을 시각적으로 구분). 가장 최근에
     * 측정한 책(recentBookId)을 미리 선택해 "이어 읽기"를 자연스럽게 한다(없으면 브라우저 기본=첫 옵션).
     *
     * <p>{@code activeStartedAt}은 화면에 시각 자체를 노출하진 않지만(사용자에겐 타임존이 보일 필요 없음),
     * 타이머 카드의 경과 계산(JS {@code data-started})에 여전히 필요하므로 모델에 남긴다.
     */
    public void populate(Model model, User user) {
        WeeklyDebt debt = debtService.weeklyDebt(user);
        Optional<ReadingSession> activeSession = sessionRepository.findActiveWithBook(user);
        Book activeBook = activeSession.map(ReadingSession::getBook).orElse(null);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("loginId", user.getLoginId()); // "내 공개 프로필" 링크(/u/{loginId})용
        model.addAttribute("remainingSeconds", debt.todayDebtSeconds()); // 헤드라인 = 오늘 부채(JS data-remaining)
        model.addAttribute("hasActiveSession", activeSession.isPresent());
        model.addAttribute("activeStartedAt", activeSession.map(ReadingSession::getStartedAt).orElse(null));
        model.addAttribute("activeBookTitle", activeBook != null ? activeBook.getTitle() : null);
        // 측정 중인 책의 누적 독서 시간(완료 세션 합). 책 미지정 측정이면 0.
        model.addAttribute("activeBookTotalSeconds",
                activeBook != null ? sessionRepository.sumDurationByUserAndBook(user, activeBook) : 0L);

        // 측정 드롭다운: "읽고싶음"을 빼고 상태별로 나눈다(읽는 중 / 완독). 같은 등록순(최신 먼저)을 유지.
        List<Book> books = bookRepository.findByUserOrderByCreatedAtDesc(user);
        model.addAttribute("readingBooks",
                books.stream().filter(b -> b.getStatus() == BookStatus.READING).toList());
        model.addAttribute("finishedBooks",
                books.stream().filter(b -> b.getStatus() == BookStatus.FINISHED).toList());

        // 가장 최근에 읽은 책을 드롭다운에서 미리 선택(이어 읽기). 측정 이력이 없으면 null → 브라우저 기본.
        List<Long> recent = sessionRepository.findRecentlyReadBookIds(user, PageRequest.of(0, 1));
        model.addAttribute("recentBookId", recent.isEmpty() ? null : recent.get(0));
    }
}
