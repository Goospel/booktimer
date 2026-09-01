package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.session.StudyCalendarService;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final UserRepository userRepository;
    private final Clock clock;

    public StudyApiController(CurrentUserService currentUserService,
                              StudySessionService studyService,
                              StudyCalendarService calendarService,
                              UserRepository userRepository,
                              Clock clock) {
        this.currentUserService = currentUserService;
        this.studyService = studyService;
        this.calendarService = calendarService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @PostMapping("/api/study/start")
    public ResponseEntity<StudyState> start(Principal principal) {
        User user = currentUserService.resolve(principal);
        Instant now = clock.instant();
        try {
            studyService.start(user, now);
        } catch (IllegalStateException e) {
            // 공부가 이미 돌고 있든 독서가 돌고 있든 사용자에겐 같은 사실이다 — 「지금 재는 중인 게 있다」.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 측정이 있습니다");
        }
        return ResponseEntity.ok(StudyState.of(studyService, user, now));
    }

    @PostMapping("/api/study/stop")
    public ResponseEntity<StudyState> stop(Principal principal) {
        User user = currentUserService.resolve(principal);
        Instant now = clock.instant();
        try {
            studyService.stop(user, now);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 측정이 없습니다");
        }
        return ResponseEntity.ok(StudyState.of(studyService, user, now));
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
        return ResponseEntity.ok(StudyState.of(studyService, user, clock.instant()));
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

    /**
     * 공부 모드 화면 상태 — 이 넷이면 히어로가 다 그려진다(부채·책이 없어 더 실을 것이 없다).
     *
     * <p>{@code todaySeconds}는 <b>완료 세션 합</b>이다 — 진행 중 몫은 클라이언트가 {@code activeStartedAt}
     * 으로 매초 더한다(독서 히어로와 같은 분업).
     *
     * <p>{@code goalSeconds}는 <b>맨 뒤에</b> 붙였다 — 대시보드·start/stop 응답이 이 레코드를 그대로
     * 실어 나르므로 옛 미니앱은 모르는 필드 하나를 무시할 뿐이다(하위호환).
     */
    public record StudyState(boolean hasActiveSession, Instant activeStartedAt, long todaySeconds, long goalSeconds) {

        static StudyState of(StudySessionService service, User user, Instant now) {
            StudySession active = service.activeSession(user);
            return new StudyState(
                    active != null,
                    active == null ? null : active.getStartedAt(),
                    service.todaySeconds(user, now),
                    user.getStudyDailyGoalSeconds());
        }
    }
}
