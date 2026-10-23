package com.booktimer.writing;

import com.booktimer.book.Book;
import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.study.StudyAiUsageService;
import com.booktimer.user.User;
import com.booktimer.writing.ClaudeWritingAssistant.Failure;
import com.booktimer.writing.ClaudeWritingAssistant.Questions;
import com.booktimer.writing.ClaudeWritingAssistant.WritingAiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 앱 글쓰기 AI 단계 — 질문(ASK) · 꼬리 질문(FOLLOW)(v1 §5.1.4 · REQ-18~22 · 27 · 46 · 47).
 *
 * <p>{@link #run}의 <b>순서가 이 클래스의 설계</b>다(REQ-19, {@code StudyRecallService.analyze} 선례): ① 동의 → ② 글 → ③ revision
 * → ④ 전제조건(라운드 상한 포함) → ⑤ 키 → ⑥ 월 예산 → ⑦ 하루 몫 → ⑧ 호출 → (실패 환불) → ⑨ 가드 → (빈 결과 환불) → ⑩ AI 열만
 * 저장. 깎을 값어치를 먼저 확정하고(④까지는 몫을 건드리지 않는다), 외부 장애로 사용자가 몫을 잃지 않게 한다.
 *
 * <p>{@code SUPPORTS} — 외부 호출(최대 90초)을 트랜잭션 밖에 둔다. 결과 저장은 호출 뒤 글을 <b>다시 읽어</b> AI 열만 바꾼다
 * ({@code @DynamicUpdate}) — 그사이 사용자가 저장한 본문을 보존한다. 본문·제목·revision은 읽기만 한다(REQ-47).
 *
 * <p>알고 수용하는 천장: 같은 글에 동시에 두 번 누르면 마지막 저장 기준이라 질문 기록 한 라운드와 글의 원가 증가분 하나가 빠질 수
 * 있다(월 누적에는 둘 다 기록된다 — 원자 UPDATE). 편당 원가를 의사결정에 쓰게 되면 원가 가산을 원자 JPQL로 바꾼다. 앱이 진행 중 버튼을 잠그고 하루 몫이 상한이다 — 서버 잠금은 v1.1(v1 §4.4).
 */
@Service
@Transactional(propagation = Propagation.SUPPORTS)
public class WritingAiService {

    private static final Logger log = LoggerFactory.getLogger(WritingAiService.class);

    /** 제품 규칙(v1 §4.1) — Phase 2가 내보낸 글의 라운드 수로 다시 정한다. */
    public static final int ROUNDS_MAX = 8;
    public static final int FOLLOW_MIN_BODY = 30;
    public static final int FOCUS_MAX = 200;

    static final String FAILED = "이번엔 질문을 만들지 못했어요 — 잠시 뒤 다시 눌러 주세요(이번 몫은 돌려 드렸어요)";

    private final WritingDraftRepository draftRepository;
    private final WritingService writingService;
    private final ClaudeWritingAssistant assistant;
    private final AiBudgetService budget;
    private final StudyAiUsageService usageService;
    private final Clock clock;

    public WritingAiService(WritingDraftRepository draftRepository, WritingService writingService,
                            ClaudeWritingAssistant assistant, AiBudgetService budget,
                            StudyAiUsageService usageService, Clock clock) {
        this.draftRepository = draftRepository;
        this.writingService = writingService;
        this.assistant = assistant;
        this.budget = budget;
        this.usageService = usageService;
        this.clock = clock;
    }

    /**
     * @param revision 앱이 마지막으로 저장한 판 — 서버 값과 다르면 409(앱은 저장을 비우고 한 번 다시 부른다)
     * @param focus    FOLLOW가 답하는 질문(띠의 질문) — 없으면 {@code null}
     * @throws ResponseStatusException 400 전제조건 · 403 동의 없음 · 404 · 409 revision · 422 거절 · 429 내 몫 · 503 키·예산·전역 몫·실패
     */
    public AiView run(User user, Long draftId, Step step, int revision, String focus) {
        if (!writingService.aiConsent(user)) { // ① 게이트가 가장 앞 — 몫·호출·비용이 모두 0이다(REQ-18)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI 질문을 받으려면 안내를 읽고 동의해 주세요");
        }
        WritingDraft draft = draftRepository.findWithBook(draftId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없어요"));
        if (draft.getRevision() != revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "글이 아직 저장되지 않았어요 — 저장 뒤 다시 눌러 주세요");
        }

        // ④ 전제조건 — 몫을 깎기 전에(REQ-20)
        String cleanFocus = step == Step.ASK || focus == null || focus.isBlank() ? null : focus.strip(); // ASK는 focus를 보지 않는다
        if (cleanFocus != null && cleanFocus.length() > FOCUS_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지금 답하는 질문이 너무 길어요(200자까지)");
        }
        QuestionLog history = QuestionLog.parse(draft.getQuestionsJson());
        if (history.rounds().size() >= ROUNDS_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 글에서 받을 수 있는 질문을 모두 받았어요 — 이제 내 글로 이어 가 보세요");
        }
        String body = draft.getBody();
        Book book = draft.getBook();
        WritingMaterial material = book == null ? null : writingService.material(user, writingService.margins(user, book));
        if (step == Step.ASK && (material == null || material.included().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여백에 문장이나 메모를 하나 이상 남긴 뒤에 질문을 받을 수 있어요");
        }
        if (step == Step.FOLLOW && body.strip().length() < FOLLOW_MIN_BODY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 생각을 한두 문장 쓴 뒤에 꼬리 질문을 받을 수 있어요");
        }
        String materialBlock = material == null ? new WritingMaterial(List.of(), 0, 0).text(null)
                : material.text(writingService.bookLine(user, book));

        if (!assistant.isEnabled()) { // ⑤
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "지금은 AI 질문이 꺼져 있어요 — 글은 그대로 쓸 수 있어요");
        }
        // 시각을 한 번만 읽는다 — 선점·환불·월 키가 같은 now로 짝을 이룬다.
        Instant now = clock.instant();
        if (!budget.hasRoom(now)) { // ⑥ 호출 전에(REQ-22)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이번 달 AI 질문이 모두 쓰였어요 — 다음 달에 다시 열려요");
        }
        // switch 식 — Grant에 값이 늘면 컴파일러가 막는다(그대로 유료 호출로 새지 않게 — StudyRecallService 선례).
        boolean granted = switch (usageService.tryConsumeBoth(user, now, Kind.WRITE)) { // ⑦
            case USER_EXHAUSTED -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "오늘 받을 수 있는 AI 질문을 다 받았어요 — 한국 시간 오전 9시에 다시 채워져요");
            case GLOBAL_EXHAUSTED -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "오늘은 AI 질문 요청이 많아 잠시 멈췄어요 — 내일 다시 받아 주세요");
            case OK -> true;
        };
        assert granted;

        // ⑧ 호출 — 앞서 받은 질문은 서버 기록에서 전부, 먼저 받은 것부터(REQ-46 — 앱 입력이 아니다)
        List<String> prior = history.rounds().stream().flatMap(r -> r.questions().stream()).map(Question::text).toList();
        WritingAiResult<Questions> result = step == Step.ASK
                ? assistant.ask(materialBlock)
                : assistant.follow(materialBlock, prior, cleanFocus, body);
        long cost = cost(step, result, materialBlock, prior, cleanFocus, body);

        if (result.failure() != null || result.value() == null) {
            usageService.refundBoth(user, now, Kind.WRITE);
            recordCostOnly(user, draftId, now, cost);
            throw failure(result.failure() == null ? Failure.UNAVAILABLE : result.failure());
        }
        String materialText = WritingGuards.materialText(materialBlock); // ⑨
        Optional<Questions> guarded = step == Step.ASK
                ? WritingGuards.ask(result.value(), materialText)
                : WritingGuards.follow(result.value(), materialText, body);
        if (guarded.isEmpty()) {
            log.warn("글쓰기 AI 결과가 가드 뒤 비었다 — user={} step={}", user.getId(), step);
            usageService.refundBoth(user, now, Kind.WRITE);
            recordCostOnly(user, draftId, now, cost);
            throw failure(Failure.UNAVAILABLE);
        }

        // ⑩ 다시 읽어 AI 열만 — 그사이 저장된 본문을 보존한다(REQ-19 · 47). 읽기·쓰기가 한 트랜잭션인 것이 요점(그쪽 javadoc).
        budget.add(now, cost);
        QuestionLog next = writingService.recordAiRound(user, draftId,
                new QuestionLog.Round(step, now, cleanFocus, body.length(), guarded.get().questions()), cost);
        return new AiView(next, now, usageService.remaining(user, now, Kind.WRITE));
    }

    /** 오늘 남은 글쓰기 AI 몫 — 화면이 버튼 옆에 그린다(UTC 날짜 키 — 소진·환불과 같은 규칙). */
    public int remainingToday(User user) {
        return usageService.remaining(user, clock.instant(), Kind.WRITE);
    }

    /** 비용 규칙(REQ-22): 응답 usage가 있으면 실제 단가, 응답 없이 끝났으면 보수 추정치, HTTP 오류는 0. */
    private long cost(Step step, WritingAiResult<Questions> result, String materialBlock, List<String> prior,
                      String focus, String body) {
        if (result.usage() != null) {
            return AiPrice.microUsd(assistant.model(), result.usage());
        }
        if (result.failure() == Failure.NO_RESPONSE) {
            int chars = ClaudeWritingAssistant.sentChars(step,
                    ClaudeWritingAssistant.userBlocks(step, materialBlock, prior, focus, body));
            return AiPrice.estimateMicroUsd(assistant.model(), chars, ClaudeWritingAssistant.maxTokens(step));
        }
        return 0;
    }

    /** 결과 없는 실패의 원가 — 월 누적과 글에(글은 원가 열만). */
    private void recordCostOnly(User user, Long draftId, Instant now, long cost) {
        if (cost <= 0) {
            return;
        }
        budget.add(now, cost);
        writingService.addAiCost(user, draftId, cost);
    }

    private static ResponseStatusException failure(Failure failure) {
        return switch (failure) {
            case REFUSED -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "이 내용으로는 AI가 질문을 만들기 어려워요 — 글은 그대로 저장돼 있어요");
            case DISABLED -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "지금은 AI 질문이 꺼져 있어요 — 글은 그대로 쓸 수 있어요");
            // 429(API 레이트리밋)·400(요청 거부)도 사용자가 고칠 것이 없는 HTTP 오류라 같은 안내 — 몫은 돌려 드렸다.
            case RATE_LIMITED, BAD_INPUT, UNAVAILABLE, NO_RESPONSE ->
                    new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, FAILED);
        };
    }

    /**
     * {@code POST /api/app/drafts/{id}/ai} 응답 — 본문·제목·revision 필드가 <b>없다</b>(REQ-47: 앱이 AI 응답으로 편집기를 채울 재료
     * 자체가 없다). 필드 이름이 앱 types.ts의 단일 출처다.
     */
    public record AiView(QuestionLog questions, Instant aiUpdatedAt, int aiRemainingToday) {
    }
}
