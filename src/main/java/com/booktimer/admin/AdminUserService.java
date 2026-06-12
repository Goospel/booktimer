package com.booktimer.admin;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.WeeklyDebt;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 관리자 데이터 조회 유스케이스 — 사용자 목록 + 드릴다운 (admin-data-lookup-design §2·§4).
 *
 * <p>전부 <b>읽기 전용</b>이다(새 저장 없음). 목록은 login_id/nickname 검색 + 페이징, 드릴다운은
 * 타이머 설정·최근 N세션·책장 요약을 한 화면용 DTO로 조립한다. <b>비밀번호 해시는 DTO에 담지 않는다.</b>
 * 세션→책 제목 같은 lazy 접근은 이 트랜잭션 안(조립 시점)에 모두 끝낸다(뷰에서 lazy 예외 방지).
 */
@Service
@Transactional(readOnly = true)
public class AdminUserService {

    /** 드릴다운 "최근 세션"으로 보여줄 최대 개수. */
    public static final int RECENT_SESSION_LIMIT = 10;

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ReadingSessionRepository sessionRepository;
    private final ReadingTimerRepository timerRepository;
    private final ReadingDebtService debtService;

    public AdminUserService(UserRepository userRepository,
                            BookRepository bookRepository,
                            ReadingSessionRepository sessionRepository,
                            ReadingTimerRepository timerRepository,
                            ReadingDebtService debtService) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.sessionRepository = sessionRepository;
        this.timerRepository = timerRepository;
        this.debtService = debtService;
    }

    /**
     * 사용자 목록(페이징). {@code q}가 있으면 login_id/nickname 부분일치(대소문자 무시)로 거르고,
     * 비어 있으면 전체를 페이징한다. email로는 검색하지 않는다(노출 최소화).
     */
    public Page<AdminUserRow> listUsers(String q, Pageable pageable) {
        Page<User> users = (q == null || q.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByLoginIdContainingIgnoreCaseOrNicknameContainingIgnoreCase(
                        q.strip(), q.strip(), pageable);
        return users.map(AdminUserRow::from);
    }

    /**
     * 한 사용자의 드릴다운 상세. 없는 loginId면 빈 Optional(컨트롤러가 404로 변환).
     * 타이머가 없으면(온보딩 전 등) {@code timer}는 null로 폴백한다.
     */
    public Optional<AdminUserDetail> userDetail(String loginId) {
        return userRepository.findByLoginId(loginId).map(this::assemble);
    }

    private AdminUserDetail assemble(User u) {
        // 타이머가 있으면 하루 목표 + 7일 윈도우 부채(오늘·이번 주)를 함께 싣는다(부채는 세션에서 유도).
        AdminUserDetail.TimerInfo timer = timerRepository.findByUser(u)
                .map(t -> {
                    WeeklyDebt debt = debtService.weeklyDebt(u);
                    return new AdminUserDetail.TimerInfo(
                            t.getDailyIncrementSeconds(),
                            debt.todayDebtSeconds(),
                            debt.totalDebtSeconds());
                })
                .orElse(null);

        List<AdminUserDetail.SessionInfo> recent = sessionRepository
                .findRecentWithBookByUser(u, PageRequest.of(0, RECENT_SESSION_LIMIT))
                .stream()
                .map(AdminUserService::toSessionInfo)
                .toList();

        AdminUserDetail.BookshelfSummary bookshelf = new AdminUserDetail.BookshelfSummary(
                bookRepository.countByUser(u),
                bookRepository.countByUserAndStatus(u, BookStatus.WANT_TO_READ),
                bookRepository.countByUserAndStatus(u, BookStatus.READING),
                bookRepository.countByUserAndStatus(u, BookStatus.FINISHED),
                bookRepository.countByUserAndVisibility(u, BookVisibility.PUBLIC));

        return new AdminUserDetail(
                u.getLoginId(),
                u.getNickname(),
                u.getEmail(),
                EmailMask.mask(u.getEmail()),
                u.getAuthProvider(),
                u.getCreatedAt(),
                u.isOnboarded(),
                u.isEmailVerified(),
                u.getRole(),
                u.getTimezone(),
                timer,
                recent,
                bookshelf);
    }

    private static AdminUserDetail.SessionInfo toSessionInfo(ReadingSession s) {
        Book book = s.getBook(); // left join fetch로 즉시 로딩됨(미지정 세션은 null)
        return new AdminUserDetail.SessionInfo(
                book == null ? null : book.getTitle(),
                s.getDurationSeconds(),
                s.getStartedAt());
    }
}
