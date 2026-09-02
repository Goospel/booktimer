package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.book.StudyBookService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.StudyCalendarService;
import com.booktimer.session.StudyHistoryService;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 공부 측정 start/stop JSON API — 미니앱 「공부」 모드가 쓰는 유일한 서버 문이다.
 *
 * <p>에러 계약은 독서({@code /api/sessions/*})와 <b>글자 그대로 같다</b>: 409 = 중복 start / 무세션 stop.
 * 두 모드가 다른 말을 하면 클라이언트가 모드마다 다른 처리를 하게 된다.
 *
 * <p>인증 라우팅은 설정 변경이 필요 없다 — {@code SecurityConfig.isMiniappApiRequest}가 Bearer 헤더 붙은
 * {@code /api/**}를 미니앱 체인으로 보내므로 {@code /api/study/**}가 자동으로 커버된다.
 */
@RestController
public class StudyApiController {

    private final CurrentUserService currentUserService;
    private final StudySessionService studyService;
    private final StudyCalendarService calendarService;
    private final StudyHistoryService historyService;
    private final StudyBookService studyBookService;
    private final StudyBookRepository studyBookRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public StudyApiController(CurrentUserService currentUserService,
                              StudySessionService studyService,
                              StudyCalendarService calendarService,
                              StudyHistoryService historyService,
                              StudyBookService studyBookService,
                              StudyBookRepository studyBookRepository,
                              UserRepository userRepository,
                              Clock clock) {
        this.currentUserService = currentUserService;
        this.studyService = studyService;
        this.calendarService = calendarService;
        this.historyService = historyService;
        this.studyBookService = studyBookService;
        this.studyBookRepository = studyBookRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * 공부 측정 시작 — {@code bookId}로 대상 책을 함께 정할 수 있다(안 줘도 시작된다).
     *
     * <p>{@code @RequestBody(required = false)}인 것이 <b>하위호환</b>이다: 12차 라이브 번들은
     * {@code {}}를 보내고 그보다 옛 클라이언트는 body가 아예 없다 — required면 배포 창 동안 공부
     * 시작이 통째로 400이 된다.
     */
    @PostMapping("/api/study/start")
    public ResponseEntity<StudyState> start(@RequestBody(required = false) StartStudyRequest request,
                                            Principal principal) {
        User user = currentUserService.resolve(principal);
        StudyBook book = request == null ? null : ownedBookOrNull(user, request.bookId());
        Instant now = clock.instant();
        try {
            studyService.start(user, now, book);
        } catch (IllegalStateException e) {
            // 공부가 이미 돌고 있든 독서가 돌고 있든 사용자에겐 같은 사실이다 — 「지금 재는 중인 게 있다」.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 측정이 있습니다");
        }
        return ResponseEntity.ok(state(user, now));
    }

    @PostMapping("/api/study/stop")
    public ResponseEntity<StudyState> stop(Principal principal) {
        User user = currentUserService.resolve(principal);
        Instant now = clock.instant();
        StudySession stopped;
        try {
            stopped = studyService.stop(user, now);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 측정이 없습니다");
        }
        // 태깅 좌표는 stop 응답에서만 산다 — 책 없이 잰 세션이면 그 id, 아니면 null(대시보드·start는 항상 null).
        // 대시보드가 최근 미태깅 세션을 실어 오면 앱에 들어올 때마다 시트가 유령처럼 뜬다.
        // getBook()==null은 lazy 프록시를 초기화하지 않는 참조 비교라 트랜잭션 밖에서도 안전(독서 stop과 같다).
        Long untaggedSessionId = stopped.getBook() == null ? stopped.getId() : null;
        return ResponseEntity.ok(StudyState.of(studyService, studyBookService, user, now, untaggedSessionId));
    }

    /**
     * <b>종료 후 태깅</b> — 책 없이 잰 측정에 나중에 책을 붙인다("무슨 책을 공부하셨나요?").
     *
     * <p>IDOR 이중 방어: 책은 {@code findByIdAndUser}로(남의 책이면 404), 세션은 서비스가 같은 방식으로
     * (남의 세션이면 404 마스킹). 독서 책장의 id도 여기선 404다 — 서재가 다른 테이블이라 애초에 없는 책이다.
     *
     * @return 200 갱신된 화면 상태 / 404 책·측정 없음 / 409 진행 중이거나 이미 책이 있는 측정
     */
    @PostMapping("/api/study/sessions/{id}/tag-book")
    public ResponseEntity<StudyState> tagBook(@PathVariable("id") Long id,
                                              @RequestBody TagBookRequest request,
                                              Principal principal) {
        User user = currentUserService.resolve(principal);
        StudyBook book = ownedBook(user, request.bookId());
        try {
            studyService.tagBook(user, id, book);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "측정을 찾을 수 없습니다"); // 세션 IDOR 마스킹
        } catch (IllegalStateException e) {
            // 독서 문구 「이미 책이 지정된 측정입니다」는 진행 중 거부를 모른다 — 두 원인을 한 문장으로 덮는다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "책을 붙일 수 없는 측정입니다");
        }
        return ResponseEntity.ok(state(user, clock.instant()));
    }

    /**
     * <b>측정 중 교체</b> — 재는 도중 대상을 바꾼다. 세션은 멈추지 않으므로 지금까지 잰 시간이 통째로
     * 새 책에 붙는다. {@code bookId}가 null이면 「책 없이」로 되돌린다.
     *
     * <p><b>세션 좌표가 요청에 없다</b> — 서버가 "내 진행 중 세션"을 찾으므로 세션 IDOR이 구조적으로
     * 성립하지 않는다. 남는 경계는 책 하나뿐이라 404로 마스킹한다(독서 {@code /api/sessions/active/book}과 같다).
     *
     * @return 200 갱신된 화면 상태 / 404 남의 책·없는 책 / 409 진행 중 측정 없음(stop과 같은 계약)
     */
    @PostMapping("/api/study/active/book")
    public ResponseEntity<StudyState> changeActiveBook(@RequestBody ChangeActiveBookRequest request,
                                                       Principal principal) {
        User user = currentUserService.resolve(principal);
        StudyBook book = ownedBookOrNull(user, request.bookId());
        try {
            studyService.changeActiveBook(user, book);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 측정이 없습니다");
        }
        return ResponseEntity.ok(state(user, clock.instant()));
    }

    /** 내 공부 책일 때만 반환 — 아니면(없음/남의 것/독서 책장의 id) 404로 존재 비노출. */
    private StudyBook ownedBook(User user, Long bookId) {
        return studyBookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
    }

    /** {@code bookId}가 null이면 「책 없이」 — 그 자체가 정당한 값이라 404가 아니다. */
    private StudyBook ownedBookOrNull(User user, Long bookId) {
        return bookId == null ? null : ownedBook(user, bookId);
    }

    private StudyState state(User user, Instant now) {
        return StudyState.of(studyService, studyBookService, user, now);
    }

    /**
     * 공부 하루 목표 설정 — <b>독서 목표와 다른 문</b>이다({@code /api/miniapp/goal}은 독서 몫).
     *
     * <p>{@code ReadingGoalService.record}를 <b>부르지 않는다</b>: 독서의 목표 변경 이력은 「그날 목표로
     * 과거를 판정」(부채 계산)하려고 있는 원장이라, 공부 값이 섞이면 그 판정이 조용히 오염된다.
     * 공부에는 이월·부채가 없어 이력이 필요 없다(V79 주석).
     *
     * @return 200 갱신된 화면 상태 / 400 목표가 음수 / 401 토큰 없음·무효(체인이 처리)
     */
    @PostMapping("/api/study/goal")
    public ResponseEntity<StudyState> setGoal(Principal principal, @RequestBody StudyGoalRequest request) {
        User user = currentUserService.resolve(principal);
        user.updateStudyDailyGoal(request.dailyGoalSeconds());
        userRepository.save(user);
        return ResponseEntity.ok(state(user, clock.instant()));
    }

    /**
     * 그 달의 공부 일정 달력 — 목표(게이지 분모)와 <b>데이터 있는 날만</b>(희소) 준다.
     *
     * @param month {@code YYYY-MM}
     * @return 200 {@link StudyCalendarResponse} / 400 달 형식 오류 / 401 토큰 없음·무효(체인이 처리)
     */
    @GetMapping("/api/study/calendar")
    public ResponseEntity<StudyCalendarResponse> calendar(Principal principal, @RequestParam String month) {
        User user = currentUserService.resolve(principal);
        return ResponseEntity.ok(new StudyCalendarResponse(
                user.getStudyDailyGoalSeconds(),
                calendarService.month(user, parseMonth(month))));
    }

    /**
     * 그날의 일정 판정을 남긴다 — <b>이 원장을 쓰는 유일한 문</b>이고, 서버는 어떤 자동 경로로도
     * 여기 오지 않는다(수동 체크가 원장이라는 요구 그대로).
     *
     * @return 200 {@code { date, kept }} / 400 날짜 형식 오류·미래 날짜 / 401
     */
    @PostMapping("/api/study/check")
    public ResponseEntity<StudyCheckResponse> check(Principal principal, @RequestBody StudyCheckRequest request) {
        User user = currentUserService.resolve(principal);
        LocalDate date = parseDate(request.date());
        calendarService.setCheck(user, date, request.kept(), clock.instant());
        return ResponseEntity.ok(new StudyCheckResponse(date, request.kept()));
    }

    /**
     * 공부 기록 — 잔디(53주)와 월별 일자 기록(전 기간). <b>판정(일정 체크)은 안 싣는다</b>: 그건
     * {@code /api/study/calendar}의 몫이고, 기록 화면은 측정 사실만 말한다.
     *
     * <p>서비스 record를 그대로 낸다 — 감싸지 않아도 {@code graph}·{@code months} 두 키가 바로 나오고,
     * {@code graph}는 대시보드·독서 기록과 <b>같은 다섯 키</b>라 미니앱이 같은 타입으로 받는다.
     *
     * @return 200 {@link StudyHistoryService.StudyHistory} / 401 토큰 없음·무효(체인이 처리)
     */
    @GetMapping("/api/study/history")
    public ResponseEntity<StudyHistoryService.StudyHistory> history(Principal principal) {
        User user = currentUserService.resolve(principal);
        return ResponseEntity.ok(historyService.history(user, clock.instant()));
    }

    /**
     * ⚠️ 이 핸들러는 <b>컨트롤러 전역</b>이라, 여기서 나가는 {@link IllegalArgumentException}의 메시지가
     * 그대로 400 본문이 되어 <b>사용자 화면에 뜬다</b>. 그래서 이 컨트롤러가 던지는 IAE 문구는 전부
     * 한국어 완성문이다(형식 오류 두 곳 · 미래 날짜는 {@code StudyCalendarService}).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /** {@code YearMonth.parse}의 {@link DateTimeParseException}은 IAE가 아니라 500이 된다 — 여기서 갈아 끼운다. */
    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("달 형식이 올바르지 않아요");
        }
    }

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않아요");
        }
    }

    /** @param dailyGoalSeconds 공부 하루 목표(초, 0 이상 — 0은 "목표 없음") */
    public record StudyGoalRequest(long dailyGoalSeconds) {
    }

    /**
     * @param date {@code YYYY-MM-DD}(유저 타임존의 달력 날짜)
     * @param kept 지킴/못 지킴, {@code null}이면 무기록으로 되돌린다(3상태 순환의 마지막 칸)
     */
    public record StudyCheckRequest(String date, Boolean kept) {
    }

    public record StudyCheckResponse(LocalDate date, Boolean kept) {
    }

    /**
     * @param goalSeconds 공부 하루 목표(초) — 달력 화면이 게이지·문구에 쓴다
     * @param days        <b>데이터 있는 날만</b>(측정이 있었거나 판정이 남은 날) 날짜순
     */
    public record StudyCalendarResponse(long goalSeconds, List<StudyCalendarService.CalendarDay> days) {
    }

    /** @param bookId 대상 공부 책(null·body 자체 생략 = 책 없이 시작) */
    public record StartStudyRequest(Long bookId) {
    }

    /** @param bookId 붙일 공부 책(필수 — 「책 없이」로 되돌리는 문은 {@code active/book}이다) */
    public record TagBookRequest(Long bookId) {
    }

    /** @param bookId 새 대상(null = 「책 없이」로 되돌리기) */
    public record ChangeActiveBookRequest(Long bookId) {
    }

    /**
     * 공부 모드 화면 상태 — 히어로·캐러셀·시트가 이 하나로 다 그려진다.
     *
     * <p>{@code todaySeconds}는 <b>완료 세션 합</b>이다 — 진행 중 몫은 클라이언트가 {@code activeStartedAt}
     * 으로 매초 더한다(독서 히어로와 같은 분업).
     *
     * <p>필드를 <b>맨 뒤에</b>만 늘린다 — 대시보드·start/stop/goal/tag/change 응답이 이 레코드를 그대로
     * 실어 나르므로 옛 미니앱은 모르는 필드를 무시할 뿐이다(하위호환). {@code goalSeconds}가 그 선례고
     * 뒤의 넷이 이번 추가다.
     *
     * @param activeBook        측정 중인 책(없거나 「책 없이」면 null) — 히어로 제목과 교체 시트의 현재 행
     * @param recentBookId      가장 최근 책을 걸고 잰 책 — 홈 캐러셀의 기본 선택
     * @param books             내 공부 서재 전체(누적 시간 포함) — 캐러셀·시트 둘이 같은 목록을 본다
     * @param untaggedSessionId <b>stop 응답에서만</b> non-null — 방금 책 없이 끝낸 측정의 태깅 좌표
     */
    public record StudyState(boolean hasActiveSession, Instant activeStartedAt, long todaySeconds, long goalSeconds,
                             StudyBookApiController.StudyBookRow activeBook, Long recentBookId,
                             List<StudyBookApiController.StudyBookRow> books, Long untaggedSessionId) {

        static StudyState of(StudySessionService service, StudyBookService bookService, User user, Instant now) {
            return of(service, bookService, user, now, null);
        }

        static StudyState of(StudySessionService service, StudyBookService bookService, User user, Instant now,
                             Long untaggedSessionId) {
            StudySession active = service.activeSession(user);   // 활성 finder가 fetch join이라 book이 로드돼 있다
            Map<Long, Long> seconds = service.totalSecondsByBook(user);
            return new StudyState(
                    active != null,
                    active == null ? null : active.getStartedAt(),
                    service.todaySeconds(user, now),
                    user.getStudyDailyGoalSeconds(),
                    active == null || active.getBook() == null
                            ? null : StudyBookApiController.StudyBookRow.from(active.getBook(), seconds),
                    service.recentBookId(user),
                    bookService.myBooks(user).stream()
                            .map(book -> StudyBookApiController.StudyBookRow.from(book, seconds))
                            .toList(),
                    untaggedSessionId);
        }
    }
}
