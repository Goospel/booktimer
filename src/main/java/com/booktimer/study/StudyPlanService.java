package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.study.ClaudeStudyAssistant.AiResult;
import com.booktimer.study.ClaudeStudyAssistant.Failure;
import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 공부 일정 원장 유스케이스 — 조회·수동 추가·삭제, 그리고 「오늘 이후 전부 교체」.
 *
 * <p>교체 규칙(설계 §2.2 A안)이 이 클래스의 요점이다: 새 일정을 적용하면 <b>오늘(유저 tz) 이후</b> 항목을
 * 전부 지우고 새것을 넣는다. 규칙이 한 문장이라 화면이 「오늘 이후 N개가 바뀝니다」 한 줄로 설명된다 —
 * 「같은 과목만 교체」는 자유 제목의 문자열 비교가 필요하고, 세대(batch) 테이블은 엔티티를 하나 더 만든다.
 * 여러 시험을 병행하는 사용은 지금 요구에 없다.
 *
 * <p>과거 항목은 <b>어떤 경로로도 지워지지 않는다</b> — 지난 계획은 기록이다. 수동 추가는 교체가 아니라
 * 순수 추가라 이 규칙 밖이다(AI가 꺼져 있어도 화면이 쓰이는 폴백 경로).
 */
@Service
@Transactional
public class StudyPlanService {

    private static final Logger log = LoggerFactory.getLogger(StudyPlanService.class);

    /** 한 번에 담을 수 있는 일정 일수 상한 — 1년 + 하루(윤년 여유). 그보다 먼 계획은 계획이 아니다. */
    public static final int MAX_DAYS = 366;

    /** 적용할 하루치 — 날짜와 그날 할 일 한 줄. */
    public record PlanDay(LocalDate date, String task) {
    }

    /**
     * @param applied 새로 넣은 항목 수
     * @param removed 교체로 지운 「오늘 이후」 항목 수 — 화면이 적용 전 확인 문구에 쓴다
     */
    public record ApplyResult(int applied, int removed) {
    }

    /** 시험일의 상한 — 1년. 그보다 먼 시험은 「일정」이 아니라 「계획」이라 날짜별 배분이 의미가 없다. */
    public static final int MAX_EXAM_DAYS_AHEAD = 365;

    /** 하루 공부 시간(분)의 하한·상한. 10분 미만은 배분이 무의미하고, 10시간 초과는 오타로 본다. */
    public static final int MIN_DAILY_MINUTES = 10;
    public static final int MAX_DAILY_MINUTES = 600;

    /** 「범위」 텍스트 상한 — {@code study_recall.scope_text}와 같은 4000자. */
    public static final int SCOPE_MAX = 4000;

    private final StudyPlanItemRepository planItemRepository;
    private final StudyAiAccessService accessService;
    private final StudyAiUsageService usageService;
    private final ClaudeStudyAssistant assistant;
    private final Clock clock;

    public StudyPlanService(StudyPlanItemRepository planItemRepository,
                            StudyAiAccessService accessService,
                            StudyAiUsageService usageService,
                            ClaudeStudyAssistant assistant,
                            Clock clock) {
        this.planItemRepository = planItemRepository;
        this.accessService = accessService;
        this.usageService = usageService;
        this.assistant = assistant;
        this.clock = clock;
    }

    /** 그 달의 일정(날짜 오름차순). 빈 달은 빈 목록이다. */
    @Transactional(readOnly = true)
    public List<StudyPlanItem> month(User user, YearMonth month) {
        return planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                user, month.atDay(1), month.atEndOfMonth());
    }

    /**
     * 일정 한 줄을 <b>추가</b>한다(교체가 아니다). 미래 날짜가 정상이고, 과거에도 하한을 두지 않는다 —
     * 지난 주를 나중에 정리하는 것은 정당한 사용이다({@code StudyCalendarService.setCheck}와 같은 규율).
     *
     * @throws IllegalArgumentException 과목·할 일이 비었거나 길이를 넘는 경우(문구가 그대로 400 본문)
     */
    public StudyPlanItem add(User user, LocalDate date, StudyBook book, String subject, String task) {
        return planItemRepository.save(StudyPlanItem.of(user, date, book, subject, task));
    }

    /**
     * 내 일정 한 줄을 지운다.
     *
     * @return 지웠으면 {@code true}, <b>없거나 남의 것이면</b> {@code false}(존재 비노출 — 컨트롤러가 404로 옮긴다)
     */
    public boolean delete(User user, Long id) {
        return planItemRepository.findByIdAndUser(id, user)
                .map(item -> {
                    planItemRepository.delete(item);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 「오늘 이후 전부 교체」 — 오늘(포함) 이후 항목을 지우고 새 일정을 넣는다. 과거는 손대지 않는다.
     *
     * <p><b>검증이 삭제보다 먼저다</b> — 순서가 반대면 잘못된 입력 하나에 남은 일정이 통째로 사라지고
     * 복구할 길이 없다(빈 목록 거부도 같은 이유의 가드다: 「적용」이 조용한 전체 삭제가 되면 안 된다).
     *
     * @throws IllegalArgumentException 빈 목록 · 오늘보다 이른 날짜 · 같은 날짜 중복 · {@value #MAX_DAYS}일 초과
     *                                  · 과목·할 일 검증 위반 — 문구가 그대로 400 본문이 된다
     */
    public ApplyResult applyReplacingFuture(User user, LocalDate today, String subject, StudyBook book,
                                            List<PlanDay> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("담을 일정이 없어요");
        }
        if (days.size() > MAX_DAYS) {
            throw new IllegalArgumentException("일정은 " + MAX_DAYS + "일까지만 담을 수 있어요");
        }
        Set<LocalDate> seen = new HashSet<>();
        for (PlanDay day : days) {
            if (day.date() == null) {
                throw new IllegalArgumentException("날짜가 없어요");
            }
            if (day.date().isBefore(today)) {
                throw new IllegalArgumentException("오늘보다 이른 날짜는 담을 수 없어요");
            }
            if (!seen.add(day.date())) {
                throw new IllegalArgumentException("같은 날짜가 두 번 들어 있어요");
            }
        }
        // 전부 만들어 본 뒤에 지운다 — 과목·할 일 검증이 여기서 터지면 기존 일정은 그대로 남는다.
        List<StudyPlanItem> items = days.stream()
                .map(day -> StudyPlanItem.of(user, day.date(), book, subject, day.task()))
                .toList();

        int removed = planItemRepository.deleteByUserAndPlanDateGreaterThanEqual(user, today);
        planItemRepository.saveAll(items);
        return new ApplyResult(items.size(), removed);
    }

    /**
     * AI에게 시험일까지의 일정 초안을 받는다 — <b>저장하지 않는다</b>.
     *
     * <p>{@link StudyRecallService#analyze}와 <b>같은 순서</b>다: ① 승인 게이트 → ② 입력 검증 →
     * ③ 키 확인 → ④ 상한 선점 → ⑤ 외부 호출 → ⑥ 실패면 환불. 게이트가 검증보다 앞인 것은 의도다 —
     * 미승인 사용자에게 「과목을 적어 주세요」를 돌려주면 승인만 받으면 쓸 수 있는 기능처럼 보인다.
     * 검증이 상한보다 앞인 것도 의도다(잘못 만든 요청으로 오늘 몫을 잃지 않게).
     *
     * <p><b>여기서 아무것도 저장되지 않는다.</b> 돌려준 초안은 화면의 미리보기로만 살아 있고, 사용자가
     * 「달력에 적용」을 눌러 {@link #applyReplacingFuture}로 다시 보내야 원장이 된다 — 그 사이에 창을
     * 닫으면 흔적이 없다.
     *
     * @return 정제된 일정 초안 + 적용하면 지워질 「오늘 이후」 항목 수
     * @throws ResponseStatusException 403 미승인 · 429 오늘 몫 소진 · 503 AI 꺼짐·응답 없음 · 400 요청 거부
     * @throws IllegalArgumentException 입력 검증 위반(문구가 그대로 400 본문)
     */
    @Transactional(propagation = Propagation.SUPPORTS) // 90초짜리 외부 호출을 트랜잭션 밖에 둔다
    public PlanDraft generate(User user, GenerateCommand command) {
        accessService.requireApproved(user); // ① 게이트가 가장 앞 — 검증·키·상한보다 먼저다

        LocalDate today = StudyDates.today(user, clock);
        String subject = requireSubject(command.subject());
        LocalDate examDate = requireExamDate(command.examDate(), today);
        requireRange(command.dailyMinutes(), MIN_DAILY_MINUTES, MAX_DAILY_MINUTES,
                "하루 공부 시간은 " + MIN_DAILY_MINUTES + "분에서 " + MAX_DAILY_MINUTES + "분 사이로 적어 주세요");
        requireRange(command.daysPerWeek(), 1, 7, "주 공부일수는 1일에서 7일 사이로 정해 주세요");
        String scope = command.scope() == null ? "" : command.scope().strip();
        if (scope.length() > SCOPE_MAX) {
            throw new IllegalArgumentException("범위는 " + SCOPE_MAX + "자까지 적을 수 있어요");
        }

        if (!assistant.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능이 꺼져 있어요");
        }
        if (!usageService.tryConsume(user, today, Kind.PLAN)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "오늘 몫을 다 썼어요 — 내일 다시 해 주세요");
        }

        AiResult<ClaudeStudyAssistant.PlanDraft> result = assistant.generatePlan(
                new ClaudeStudyAssistant.PlanInput(subject, scope, today, examDate,
                        command.dailyMinutes(), command.daysPerWeek()));
        if (!result.ok()) {
            usageService.refund(user, today, Kind.PLAN);
            throw failure(result.failure());
        }
        List<ClaudeStudyAssistant.PlanDay> days = ClaudeStudyAssistant.sanitizePlan(
                result.value().days(), today, examDate, command.daysPerWeek());
        if (days.isEmpty()) {
            // 형식은 맞는데 쓸 날짜가 하나도 안 남았다 — 빈 미리보기를 「완성」이라 부를 수 없다.
            log.warn("Claude 일정 초안이 정제 후 비어 돌려주지 않는다 — user={}", user.getId());
            usageService.refund(user, today, Kind.PLAN);
            throw failure(Failure.UNAVAILABLE);
        }
        return new PlanDraft(days, planItemRepository.countByUserAndPlanDateGreaterThanEqual(user, today));
    }

    /** 오늘 남은 일정 생성 몫 — 화면이 버튼 옆에 그린다. */
    public int remainingPlan(User user) {
        return usageService.remaining(user, StudyDates.today(user, clock), Kind.PLAN);
    }

    private static String requireSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("과목을 입력해 주세요");
        }
        String trimmed = subject.strip();
        if (trimmed.length() > StudyPlanItem.SUBJECT_MAX) {
            throw new IllegalArgumentException("과목은 " + StudyPlanItem.SUBJECT_MAX + "자까지 쓸 수 있어요");
        }
        return trimmed;
    }

    /** 시험일은 <b>내일 이후 1년 안</b>이다 — 오늘이 시험이면 짤 일정이 없고, 1년 뒤는 날짜 배분이 무의미하다. */
    private static LocalDate requireExamDate(LocalDate examDate, LocalDate today) {
        if (examDate == null) {
            throw new IllegalArgumentException("시험일을 골라 주세요");
        }
        if (!examDate.isAfter(today)) {
            throw new IllegalArgumentException("시험일은 내일 이후로 정해 주세요");
        }
        if (examDate.isAfter(today.plusDays(MAX_EXAM_DAYS_AHEAD))) {
            throw new IllegalArgumentException("시험일은 1년 안으로 정해 주세요");
        }
        return examDate;
    }

    private static void requireRange(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 어댑터의 실패 갈래를 HTTP로 옮긴다 — <b>일정 경로의 문구</b>다.
     *
     * <p>{@code BAD_INPUT}이 「사진」도 「글」도 말하지 않는 것에 유의(문구는 엔드포인트마다 다르다).
     */
    private static ResponseStatusException failure(Failure failure) {
        return switch (failure) {
            case DISABLED -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능이 꺼져 있어요");
            case RATE_LIMITED -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요");
            case BAD_INPUT -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 범위로는 일정을 만들 수 없어요");
            case UNAVAILABLE -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "일정을 만들지 못했어요 — 잠시 후 다시 시도해 주세요");
        };
    }

    /**
     * 일정 생성 요청 — 화면이 채운 폼 그대로.
     *
     * @param scope 공부할 범위 원문. 비어 있어도 된다(그때는 프롬프트가 「단원을 지어내지 마라」고 못 박는다)
     */
    public record GenerateCommand(String subject, String scope, LocalDate examDate,
                                  int dailyMinutes, int daysPerWeek) {
    }

    /**
     * 미리보기로 돌려주는 초안 — <b>저장 전</b>이다.
     *
     * @param replaceCount 지금 적용하면 지워질 「오늘 이후」 항목 수. <b>생성 시점에 센 값</b>이라,
     *                     사용자가 미리보기를 읽는 동안 일정을 더하면 실제 {@code removed}가 더 클 수 있다
     */
    public record PlanDraft(List<ClaudeStudyAssistant.PlanDay> days, int replaceCount) {
    }
}
