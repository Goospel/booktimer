package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.study.StudyDates;
import com.booktimer.study.StudyPlanItem;
import com.booktimer.study.StudyPlanService;
import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
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

/**
 * 웹 「공부」 화면의 일정 원장 문 — 달력 상세가 쓰는 조회 하나와 뮤테이션 둘.
 *
 * <p><b>세션 인증이다</b>(Bearer 아님). {@code SecurityConfig}의 미니앱 체인 스위치가 Authorization 헤더
 * 유무라, 헤더 없는 {@code /api/**}는 그대로 세션 체인(CSRF 포함)으로 흐른다 — 그래서 이 화면은 기존
 * {@code /api/study/calendar}·{@code /api/study/check}를 <b>신설 없이 재사용</b>한다(설계 §1.2).
 *
 * <p>에러 계약은 {@link StudyApiController}와 같다: IAE → 400(문구가 그대로 본문) · 남의 것·없는 것 → 404.
 *
 * <p>{@link Agenda}의 {@code aiEnabled}·{@code remaining}·{@code recalls}는 <b>지금은 항상 꺼짐/빈 값</b>이다 —
 * 필드를 미리 두는 이유는 화면이 이 응답 하나만 보고 그려지기 때문이다(AI가 붙는 판에 응답 모양이 바뀌면
 * 섬 전체가 같이 흔들린다). 뒤에 붙는 판이 값을 채운다.
 */
@RestController
public class StudyPlanApiController {

    private final CurrentUserService currentUserService;
    private final StudyPlanService planService;
    private final StudyBookRepository studyBookRepository;
    private final Clock clock;

    public StudyPlanApiController(CurrentUserService currentUserService,
                                  StudyPlanService planService,
                                  StudyBookRepository studyBookRepository,
                                  Clock clock) {
        this.currentUserService = currentUserService;
        this.planService = planService;
        this.studyBookRepository = studyBookRepository;
        this.clock = clock;
    }

    /**
     * 그 달의 일정 + 화면이 「오늘」을 판정하는 데 쓰는 서버 기준 날짜.
     *
     * <p>{@code today}를 서버가 주는 것이 요점이다 — 기기 타임존이 유저 설정과 어긋나도 미래 잠금 판정이
     * 서버({@code StudyCalendarService.setCheck})와 같아진다(어긋나면 화면이 허용한 탭이 400으로 튕긴다).
     *
     * @param month {@code YYYY-MM}
     * @return 200 {@link Agenda} / 400 달 형식 오류
     */
    @GetMapping("/api/study/agenda")
    public ResponseEntity<Agenda> agenda(Principal principal, @RequestParam String month) {
        User user = currentUserService.resolve(principal);
        List<PlanItemRow> items = planService.month(user, parseMonth(month)).stream()
                .map(PlanItemRow::from)
                .toList();
        return ResponseEntity.ok(new Agenda(
                StudyDates.today(user, clock),
                user.getStudyAiAccess(),
                user.getStudyAiAccessAt(),
                false,                       // AI는 다음 판 — 지금은 저장·수동 편집만 한다
                new Remaining(0, 0, 0),
                items,
                List.of()));
    }

    /**
     * 일정 한 줄 수동 추가 — AI가 없어도(그리고 꺼져 있어도) 이 화면이 쓰이게 하는 경로다.
     *
     * @return 200 {@link PlanItemRow} / 400 검증 위반·날짜 형식 / 404 남의 bookId
     */
    @PostMapping("/api/study/plan/items")
    public ResponseEntity<PlanItemRow> addItem(Principal principal, @RequestBody AddItemRequest request) {
        User user = currentUserService.resolve(principal);
        StudyBook book = ownedBookOrNull(user, request.bookId());
        StudyPlanItem item = planService.add(user, parseDate(request.date()), book,
                request.subject(), request.task());
        return ResponseEntity.ok(PlanItemRow.from(item));
    }

    /**
     * 일정 한 줄 삭제. 편집은 없다(추가·삭제로 충분하다는 것이 이번 판의 범위다).
     *
     * @return 200 / 404 없거나 남의 것(존재 비노출)
     */
    @PostMapping("/api/study/plan/items/{id}/delete")
    public ResponseEntity<Void> deleteItem(Principal principal, @PathVariable("id") Long id) {
        User user = currentUserService.resolve(principal);
        if (!planService.delete(user, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * ⚠️ 컨트롤러 전역이라, 여기서 나가는 {@link IllegalArgumentException}의 메시지가 그대로 400 본문이
     * 되어 <b>사용자 화면에 뜬다</b>({@link StudyApiController}와 같은 규약) — 그래서 이 경로가 던지는
     * IAE 문구는 전부 한국어 완성문이다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /** 내 공부 책일 때만 — 아니면(없음/남의 것/독서 책장의 id) 404로 존재 비노출. null은 「책 없이」라 정당하다. */
    private StudyBook ownedBookOrNull(User user, Long bookId) {
        if (bookId == null) {
            return null;
        }
        return studyBookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
    }

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

    /**
     * @param today       유저 타임존 기준 오늘 — 화면의 미래 잠금이 서버와 같은 날을 보게 하는 기준
     * @param aiAccess    관리자 승인 상태 — 화면의 AI 상태 줄(신청 버튼·대기·거절 문구)이 이걸로 갈린다
     * @param aiAccessAt  마지막 상태 전이 시각(「M월 D일 신청」 표시용). 신청 전이면 {@code null}
     * @param aiEnabled   AI 기능 사용 가능 여부(키 있음 AND 승인됨) — 이번 판에선 항상 {@code false}.
     *                    <b>승인만으론 켜지지 않는다</b>: 키가 붙는 다음 판에서야 true가 될 수 있다
     * @param remaining   오늘 남은 AI 호출 몫 — 이번 판에선 전부 0
     * @param items       그 달의 일정(날짜 오름차순)
     * @param recalls     그 달 + 전달 말일의 백지복습 표식 — 이번 판에선 빈 목록
     */
    public record Agenda(LocalDate today, StudyAiAccess aiAccess, Instant aiAccessAt,
                         boolean aiEnabled, Remaining remaining,
                         List<PlanItemRow> items, List<RecallRow> recalls) {
    }

    public record Remaining(int plan, int transcribe, int analyze) {
    }

    /** @param bookId 대상 공부 책(자유 제목이거나 책이 삭제됐으면 null — subject가 제목을 대신 든다) */
    public record PlanItemRow(Long id, LocalDate date, Long bookId, String subject, String task) {

        static PlanItemRow from(StudyPlanItem item) {
            StudyBook book = item.getBook();
            return new PlanItemRow(item.getId(), item.getPlanDate(),
                    book == null ? null : book.getId(), item.getSubject(), item.getTask());
        }
    }

    /**
     * 달력 칸의 복습 표식 — 그날 복습이 있었나({@code 복습}), 그 복습에 다음날 문제가 붙었나.
     * 다음 판에서 채운다.
     */
    public record RecallRow(LocalDate date, boolean analyzed, boolean hasQuestions) {
    }

    /** @param bookId 대상 공부 책(null·생략 = 자유 제목) */
    public record AddItemRequest(String date, Long bookId, String subject, String task) {
    }
}
