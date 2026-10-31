package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.security.CurrentUserService;
import com.booktimer.story.Story;
import com.booktimer.user.User;
import com.booktimer.writing.BookLine;
import com.booktimer.writing.QuestionLog;
import com.booktimer.writing.Step;
import com.booktimer.writing.WritingAiService;
import com.booktimer.writing.WritingDraft;
import com.booktimer.writing.WritingMaterial;
import com.booktimer.writing.WritingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

/**
 * 아이폰 앱 「책생글」의 글 원장 · 책 재료 · AI 동의(v1 §5.1.3 PR-B) — {@code /api/app/**}는 Bearer 체인이다(토큰 없으면 401).
 *
 * <p>에러 계약은 {@link StudyNoteApiController}와 같다: IAE 문구가 그대로 400 본문, 없는·남의 것은 404(존재 비노출),
 * {@link ResponseStatusException}의 사유는 평문 본문(404·409·503).
 *
 * <p>응답 record의 필드 이름이 앱 {@code types.ts}의 단일 출처다 — 이름을 바꾸면 앱이 조용히 {@code undefined}를 읽는다.
 *
 * <p><b>글(drafts)은 여백 쪽지로 옮겼다</b>(2026-10-30 책생글 M1 — D9 · J2 · J5). 옛 JS 호환 표(설계 §5.1): 쓰기(POST · PUT · ai ·
 * DELETE)는 행과 무관하게 410 평문, GET 한 편은 {@code movedTo}(옮긴 쪽지 id), 목록은 읽기 전용 200, materials의 {@code drafts}는
 * 늘 {@code []}. <b>404를 쓰지 않는다</b> — 옛 {@code draftSync}는 404에서 기기 칸을 지우고 410에서는 남긴다(새 JS가 옮긴다).
 * 쪽지 API는 {@link AppMarginApiController}.
 */
@RestController
public class WritingApiController {

    private final CurrentUserService currentUserService;
    private final WritingService writingService;
    private final WritingAiService aiService;

    public WritingApiController(CurrentUserService currentUserService, WritingService writingService,
                                WritingAiService aiService) {
        this.currentUserService = currentUserService;
        this.writingService = writingService;
        this.aiService = aiService;
    }

    /** 옛 JS 쓰기 응답 — 평문(앱 {@code errorMessage}가 본문 글자를 그대로 화면 문구로 쓴다 — JSON이면 괄호가 화면에 뜬다). */
    static final String MOVED = "이 글은 여백으로 옮겨졌어요 — 앱을 닫았다가 다시 열어 주세요";

    private static ResponseStatusException gone() {
        return new ResponseStatusException(HttpStatus.GONE, MOVED);
    }

    /** 최근 고친 글 20편(책이 풀린 글 포함, 본문 없음) — 읽기 전용(옛 홈의 「이어 쓰기」 — M2 뒤 410). */
    @GetMapping("/api/app/drafts")
    public List<DraftSummary> recent(Principal principal) {
        return writingService.recent(currentUserService.resolve(principal)).stream().map(DraftSummary::from).toList();
    }

    /** @return 410 — 새 글은 여백 쪽지로({@code POST /api/app/margins}) */
    @PostMapping("/api/app/drafts")
    public void create() {
        throw gone();
    }

    /** @return 200({@code movedTo} = 옮긴 쪽지 id · 안 옮긴 빈 글은 null) / 404 남의·없는 글 */
    @GetMapping("/api/app/drafts/{id}")
    public DraftView get(@PathVariable Long id, Principal principal) {
        User user = currentUserService.resolve(principal);
        return view(user, writingService.get(user, id));
    }

    /** @return 410 — 옮긴 글이든 아니든(옛 {@code draftSync}는 410에 칸을 남긴다 · 칸은 새 JS가 옮긴다) */
    @PutMapping("/api/app/drafts/{id}")
    public void save(@PathVariable Long id) {
        throw gone();
    }

    /** @return 410 — 표를 얼린다(M2 전 따라잡기 검사가 셀 수 있게) */
    @DeleteMapping("/api/app/drafts/{id}")
    public void delete(@PathVariable Long id) {
        throw gone();
    }

    /**
     * 그 책의 여백·AI 재료 개수 — 본인 책이면 ADMIN·핸들 없는 계정도(REQ-11). {@code drafts}는 늘 {@code []}(REQ-216 — 옛 책 화면이
     * {@code data.drafts.length}를 읽어 필드를 빼면 깨진다). @return 200 / 404 남의·없는 책
     */
    @GetMapping("/api/app/books/{id}/materials")
    public MaterialsView materials(@PathVariable Long id, Principal principal) {
        User user = currentUserService.resolve(principal);
        Book book = writingService.ownedBook(user, id);
        List<Story> margins = writingService.margins(user, book);
        WritingMaterial material = writingService.material(user, margins);
        return new MaterialsView(writingService.bookLine(user, book),
                margins.stream().map(MarginItem::from).toList(),
                material.included().size(), material.excludedCount(),
                List.of(), writingService.aiConsent(user));
    }

    /** @return 410 — AI 질문은 쪽지에서({@code POST /api/app/margins/{id}/ai}) */
    @PostMapping("/api/app/drafts/{id}/ai")
    public void ai(@PathVariable Long id) {
        throw gone();
    }

    /** @return 200 / 503 처리방침 6-3절 시행일 전(REQ-15) */
    @PutMapping("/api/app/ai-consent")
    public ConsentView consent(@RequestBody ConsentRequest request, Principal principal) {
        User user = writingService.consent(currentUserService.resolve(principal), request.agree());
        return new ConsentView(writingService.aiConsent(user), user.getAiConsentAt());
    }

    /** 앱 설정의 동의 상태(REQ-154) — PUT과 같은 모양. 시행일보다 이른 시각은 aiConsent=false다. */
    @GetMapping("/api/app/ai-consent")
    public ConsentView consentView(Principal principal) {
        User user = currentUserService.resolve(principal);
        return new ConsentView(writingService.aiConsent(user), user.getAiConsentAt());
    }

    private DraftView view(User user, WritingDraft draft) {
        Book book = draft.getBook();
        return new DraftView(draft.getId(), book == null ? null : book.getId(), book == null ? null : book.getTitle(),
                draft.getTitle(), draft.getBody(), draft.getRevision(), QuestionLog.parse(draft.getQuestionsJson()),
                draft.getAiUpdatedAt(), aiService.remainingToday(user), writingService.aiConsent(user), draft.getStoryId());
    }

    /** IAE 문구가 그대로 400 본문(text/plain — 반사 XSS 방지, charset 명시는 한글 깨짐 방지). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> plainText(ResponseStatusException e) {
        return AppAuthApiController.plainTextError(e);
    }

    // ── DTO (엔티티 직렬화 금지 — 평탄 record 화이트리스트) ───────────────────

    public record ConsentRequest(boolean agree) {}

    /**
     * @param revision 앱이 마지막으로 저장한 판 — 다르면 409(앱은 저장을 비우고 한 번 다시 부른다)
     * @param focus    FOLLOW가 답하는 질문(띠) — 없으면 null. 200자까지
     */
    public record AiStep(Step step, int revision, String focus) {}

    /** @param aiConsent 지금 유효한 동의인가(시행일 이후 시각) · @param aiConsentAt 박힌 시각(철회면 null) */
    public record ConsentView(boolean aiConsent, Instant aiConsentAt) {}

    /** @param preview 첫 비공백 줄 80자 · @param chars 본문 글자 수 · @param exported 한 번이라도 내보냈나 */
    public record DraftSummary(Long id, Long bookId, String bookTitle, String title, String preview,
                               int chars, Instant updatedAt, boolean exported) {
        static DraftSummary from(WritingDraft d) {
            Book book = d.getBook();
            return new DraftSummary(d.getId(), book == null ? null : book.getId(), book == null ? null : book.getTitle(),
                    d.getTitle(), d.preview(), d.getBody().length(), d.getUpdatedAt(), d.getExportedAt() != null);
        }
    }

    /**
     * 글 한 편(본문 포함).
     *
     * @param questions        받은 질문 기록 — 없으면 rounds = []
     * @param aiRemainingToday 오늘 남은 글쓰기 AI 몫({@code Kind.WRITE} — UTC 날짜, KST 오전 9시에 다시 채워진다)
     * @param movedTo          옮긴 쪽지 id(V105 — 책생글 M1) · 안 옮긴 빈 글은 null. 새 JS의 기기 칸 옮기기가 이 필드의 <b>유무</b>로
     *                         M1 전 서버를 가린다(필드가 없으면 칸을 건드리지 않는다 — J96)
     */
    public record DraftView(Long id, Long bookId, String bookTitle, String title, String body, int revision,
                            QuestionLog questions, Instant aiUpdatedAt, int aiRemainingToday, boolean aiConsent,
                            Long movedTo) {}

    public record MarginItem(Long id, String quote, String text, Instant createdAt) {
        static MarginItem from(Story s) {
            return new MarginItem(s.getId(), s.getQuote(), s.getText(), s.getCreatedAt());
        }
    }

    /**
     * @param margins    그 책 여백 — 최근순 전부(최대 200)
     * @param aiIncluded AI에 보내는 여백 수 · @param aiExcluded 상한에 걸려 빠지는 수(「재료 N개 중 M개를 보냈어요」 — REQ-12)
     */
    public record MaterialsView(BookLine book, List<MarginItem> margins, int aiIncluded, int aiExcluded,
                                List<DraftSummary> drafts, boolean aiConsent) {}
}
