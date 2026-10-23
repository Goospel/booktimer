package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.security.CurrentUserService;
import com.booktimer.story.Story;
import com.booktimer.user.User;
import com.booktimer.writing.BookLine;
import com.booktimer.writing.QuestionLog;
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
 */
@RestController
public class WritingApiController {

    private final CurrentUserService currentUserService;
    private final WritingService writingService;

    public WritingApiController(CurrentUserService currentUserService, WritingService writingService) {
        this.currentUserService = currentUserService;
        this.writingService = writingService;
    }

    /** 최근 고친 글 20편(책이 풀린 글 포함, 본문 없음). */
    @GetMapping("/api/app/drafts")
    public List<DraftSummary> recent(Principal principal) {
        return writingService.recent(currentUserService.resolve(principal)).stream().map(DraftSummary::from).toList();
    }

    /** @return 201 새 글 / 400 bookId 없음 / 404 남의·없는 책·공부 서재 id(REQ-48) */
    @PostMapping("/api/app/drafts")
    public ResponseEntity<DraftView> create(@RequestBody CreateDraft request, Principal principal) {
        User user = currentUserService.resolve(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(user, writingService.create(user, request.bookId())));
    }

    /** @return 200 / 404 남의·없는 글 */
    @GetMapping("/api/app/drafts/{id}")
    public DraftView get(@PathVariable Long id, Principal principal) {
        User user = currentUserService.resolve(principal);
        return view(user, writingService.get(user, id));
    }

    /** @return 200(revision +1) / 400 길이 / 404 남의·없는 글 / 409 낡은 revision(덮어쓰지 않는다) */
    @PutMapping("/api/app/drafts/{id}")
    public DraftView save(@PathVariable Long id, @RequestBody SaveDraft request, Principal principal) {
        User user = currentUserService.resolve(principal);
        return view(user, writingService.save(user, id, request.title(), request.body(), request.revision(),
                request.exported()));
    }

    /** @return 204 / 404 남의·없는 글 */
    @DeleteMapping("/api/app/drafts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        writingService.delete(currentUserService.resolve(principal), id);
        return ResponseEntity.noContent().build();
    }

    /** 그 책의 여백·글·AI 재료 개수 — 본인 책이면 ADMIN·핸들 없는 계정도(REQ-11). @return 200 / 404 남의·없는 책 */
    @GetMapping("/api/app/books/{id}/materials")
    public MaterialsView materials(@PathVariable Long id, Principal principal) {
        User user = currentUserService.resolve(principal);
        Book book = writingService.ownedBook(user, id);
        List<Story> margins = writingService.margins(user, book);
        WritingMaterial material = writingService.material(user, margins);
        return new MaterialsView(writingService.bookLine(user, book),
                margins.stream().map(MarginItem::from).toList(),
                material.included().size(), material.excludedCount(),
                writingService.draftsOf(user, book).stream().map(DraftSummary::from).toList(),
                writingService.aiConsent(user));
    }

    /** @return 200 / 503 처리방침 6-3절 시행일 전(REQ-15) */
    @PutMapping("/api/app/ai-consent")
    public ConsentView consent(@RequestBody ConsentRequest request, Principal principal) {
        User user = writingService.consent(currentUserService.resolve(principal), request.agree());
        return new ConsentView(writingService.aiConsent(user), user.getAiConsentAt());
    }

    private DraftView view(User user, WritingDraft draft) {
        Book book = draft.getBook();
        return new DraftView(draft.getId(), book == null ? null : book.getId(), book == null ? null : book.getTitle(),
                draft.getTitle(), draft.getBody(), draft.getRevision(), QuestionLog.parse(draft.getQuestionsJson()),
                draft.getAiUpdatedAt(), writingService.aiConsent(user));
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

    /** @param bookId 독서 서재 책 id — 공부 서재 id는 404(REQ-48) */
    public record CreateDraft(Long bookId) {}

    /**
     * @param revision 클라가 마지막으로 받은 판 — 서버 값과 다르면 409
     * @param exported 공유 시트 sharedAction일 때만 true. 래퍼 Boolean — 빠진 요청이 실패하지 않게
     */
    public record SaveDraft(String title, String body, int revision, Boolean exported) {}

    public record ConsentRequest(boolean agree) {}

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
     * 글 한 편(본문 포함). v1 §5.1.3의 {@code aiRemainingToday}는 하루 몫 종류({@code Kind.WRITE})가 생기는 PR-C가
     * 함께 더한다 — 지금 넣으면 0이라는 거짓 값이 된다.
     *
     * @param questions 받은 질문 기록 — 없으면 rounds = []
     */
    public record DraftView(Long id, Long bookId, String bookTitle, String title, String body, int revision,
                            QuestionLog questions, Instant aiUpdatedAt, boolean aiConsent) {}

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
