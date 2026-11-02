package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.session.StudySessionService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;

/**
 * <b>앱 기기 측정 마감</b> — 앱이 기기에서 잰 끝난 측정(시작 · 끝 = 기기가 누른 시각)을 올린다. 앱은 측정이 끝나기 전엔
 * 서버에 알리지 않는다(2026-11-02 결정) — 그래서 이 문은 <b>새 행만 적는다</b>: 있던 행(웹에서 연 측정 · 끝난 기록 · 남의 행)은
 * 닫지도 줄이지도 지우지도 않는다. 이미 있는 실측과 겹친 시간은 빼고 적는다(서버에 먼저 있는 실측이 이긴다).
 * <p>에러 계약 — <b>422는 아래 {@link #check} 한 곳에서만 낸다</b>(앱은 422에서만 칸을 지운다):
 * 422 = 끝 &lt; 시작 · 시작이 지금 + 5분보다 늦음 · 시작이 7일보다 오래됨(날짜 정책 — import 문과 같은 5분 · 7일).
 * 409 = 끝이 지금보다 늦음(기기 시계가 앞섬 — 아무것도 적지 않는다. 앱은 칸을 남기고 다시 보낸다).
 * 400 = 본문 · 시각 파싱 실패 · 시각 없음(전역 처리기 · parseInstant — 앱은 칸을 남긴다). 401 = 체인. 그 밖은 500.
 */
@RestController
public class MeasureFinishApiController {

    private final CurrentUserService currentUserService;
    private final ReadingSessionService readingService;
    private final StudySessionService studyService;
    private final BookRepository bookRepository;
    private final StudyBookRepository studyBookRepository;
    private final Clock clock;

    public MeasureFinishApiController(CurrentUserService currentUserService,
                                      ReadingSessionService readingService,
                                      StudySessionService studyService,
                                      BookRepository bookRepository,
                                      StudyBookRepository studyBookRepository,
                                      Clock clock) {
        this.currentUserService = currentUserService;
        this.readingService = readingService;
        this.studyService = studyService;
        this.bookRepository = bookRepository;
        this.studyBookRepository = studyBookRepository;
        this.clock = clock;
    }

    @PostMapping("/api/sessions/finish")
    public FinishResponse finishReading(@RequestBody FinishRequest req, Principal principal) {
        User user = currentUserService.resolve(principal);
        Instant now = clock.instant();
        Interval i = check(DashboardApiController.parseInstant(req.startedAt()),
                DashboardApiController.parseInstant(req.endedAt()), now);
        // 지워진 · 남의 책이면 책 없이 — 시간을 잃지 않는다(응답이 같아 남의 책 존재를 알리지 않는다)
        Book book = req.bookId() == null ? null : bookRepository.findByIdAndUser(req.bookId(), user).orElse(null);
        return new FinishResponse(readingService.finish(user, i.start(), i.end(), book, now));
    }

    @PostMapping("/api/study/finish")
    public FinishResponse finishStudy(@RequestBody FinishRequest req, Principal principal) {
        User user = currentUserService.resolve(principal);
        Instant now = clock.instant();
        Interval i = check(DashboardApiController.parseInstant(req.startedAt()),
                DashboardApiController.parseInstant(req.endedAt()), now);
        StudyBook book = req.bookId() == null ? null
                : studyBookRepository.findByIdAndUser(req.bookId(), user).orElse(null);
        return new FinishResponse(studyService.finish(user, i.start(), i.end(), book, now));
    }

    /** 날짜 정책(순수 — 경계는 고정 시각 단위 테스트가 본다). 422만 앱이 지운다. */
    static Interval check(Instant s, Instant e, Instant now) {
        if (e.isBefore(s)
                || s.isAfter(now.plus(DashboardApiController.IMPORT_FUTURE_TOLERANCE))
                || s.isBefore(now.minus(DashboardApiController.IMPORT_MAX_AGE))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "올릴 수 없는 측정 구간입니다");
        }
        if (e.isAfter(now)) { // 아직 오지 않은 시각 — 적지 않고 미룬다(다시 보내면 기기 시각 그대로 적힌다)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "아직 끝나지 않은 구간입니다");
        }
        return new Interval(s, e);
    }

    record Interval(Instant start, Instant end) {}

    /** 시각은 ISO-8601 문자열(파싱 실패는 400 — 앱은 칸을 남긴다) · bookId 선택 */
    public record FinishRequest(String startedAt, String endedAt, Long bookId) {}

    /** 다른 곳의 실측과 겹쳐 빼고 적은 초 — 이 구간의 시작 앞에서 오거나 끝 뒤로 가는 실측만 센다 */
    public record FinishResponse(long trimmedSeconds) {}
}
