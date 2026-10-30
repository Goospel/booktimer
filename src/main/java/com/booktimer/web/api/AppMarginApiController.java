package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.security.CurrentUserService;
import com.booktimer.story.Story;
import com.booktimer.story.StoryService;
import com.booktimer.user.User;
import com.booktimer.web.api.WritingApiController.AiStep;
import com.booktimer.writing.QuestionLog;
import com.booktimer.writing.WritingAiService;
import com.booktimer.writing.WritingAiService.AiView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

/**
 * 앱 「책생글」의 여백 쪽지 API(책생글 「여백 + 블로그」 §5.1 · M1) — {@code /api/app/**}는 Bearer 체인이다(토큰 없으면 401).
 *
 * <p>쪽지 = {@code story} 행. v1의 「여백 남기기」와 「이 책으로 글쓰기」를 하나로 합쳤다(D9). 책 없는 쪽지도 된다(C9).
 * 전부 <b>본인 것만</b> — 남의 · 없는 쪽지와 책은 404(존재 비노출). 웹 · 동결 미니앱의 옛 경로({@code /api/stories/**})는 그대로 남는다.
 *
 * <p>에러 계약은 {@link WritingApiController}와 같다: IAE 문구가 그대로 400 본문, {@link ResponseStatusException}의 사유는 평문
 * 본문(404 · 409 · 429 · 503). 응답은 늘 {@code revision}을 준다(앱의 기기 칸 옮기기가 이 판으로 보낸다).
 */
@RestController
public class AppMarginApiController {

    private final CurrentUserService currentUserService;
    private final StoryService storyService;
    private final WritingService writingService;
    private final WritingAiService aiService;

    public AppMarginApiController(CurrentUserService currentUserService, StoryService storyService,
                                  WritingService writingService, WritingAiService aiService) {
        this.currentUserService = currentUserService;
        this.storyService = storyService;
        this.writingService = writingService;
        this.aiService = aiService;
    }

    /** 그 책의 쪽지 — 최근순. @return 200 / 404 남의·없는·공부 서재 책 */
    @GetMapping("/api/app/books/{bookId}/margins")
    public List<MarginSummary> ofBook(@PathVariable Long bookId, Principal principal) {
        User user = currentUserService.resolve(principal);
        Book book = writingService.ownedBook(user, bookId);
        return writingService.margins(user, book).stream().map(MarginSummary::from).toList();
    }

    /** 책 없는 쪽지 — 최근순(C9). {@code book}은 지금 {@code none}만 받는다. @return 200 / 400 */
    @GetMapping("/api/app/margins")
    public List<MarginSummary> bookless(@RequestParam String book, Principal principal) {
        if (!"none".equals(book)) {
            throw new IllegalArgumentException("book=none만 받아요");
        }
        return storyService.booklessNotes(currentUserService.resolve(principal)).stream().map(MarginSummary::from).toList();
    }

    /** @return 200 / 404 남의·없는 쪽지 */
    @GetMapping("/api/app/margins/{id}")
    public MarginView get(@PathVariable Long id, Principal principal) {
        User user = currentUserService.resolve(principal);
        return view(user, storyService.note(user, id));
    }

    /** 첫 저장 때 행을 만든다(J6). @return 201 / 400 길이·빈 쪽지·책 없는 옮긴 문장 / 404 남의·없는·공부 서재 책 / 429 시간당 30 */
    @PostMapping("/api/app/margins")
    public ResponseEntity<MarginView> create(@RequestBody CreateMargin request, Principal principal) {
        User user = currentUserService.resolve(principal);
        Story note = storyService.createNote(user, request.bookId(), request.quote(), request.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(user, note));
    }

    /** @return 200(revision +1) / 400 / 404 / 409 낡은 판(덮어쓰지 않는다) */
    @PutMapping("/api/app/margins/{id}")
    public MarginView edit(@PathVariable Long id, @RequestBody EditMargin request, Principal principal) {
        User user = currentUserService.resolve(principal);
        return view(user, storyService.editNote(user, id, request.quote(), request.text(), request.revision(),
                request.exported()));
    }

    /** @return 204 / 404 남의·없는 쪽지 */
    @DeleteMapping("/api/app/margins/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        storyService.delete(currentUserService.resolve(principal), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 쪽지 AI 질문(ASK) · 꼬리 질문(FOLLOW) 한 번 — v1 {@code /drafts/{id}/ai}와 같은 몸 · 같은 상태 코드. 응답에 생각 · 옮긴 문장 ·
     * revision이 없다(REQ-47 · REQ-221 — 앱이 AI 응답으로 입력칸을 채울 재료가 없다).
     *
     * @return 200 / 400 전제조건 / 403 동의 없음 / 404 남의·없는 쪽지 / 409 revision 다름 / 422 거절 / 429 내 하루 몫 / 503
     */
    @PostMapping("/api/app/margins/{id}/ai")
    public AiView ai(@PathVariable Long id, @RequestBody AiStep request, Principal principal) {
        if (request.step() == null) {
            throw new IllegalArgumentException("단계를 골라 주세요");
        }
        return aiService.run(currentUserService.resolve(principal), id, request.step(), request.revision(), request.focus());
    }

    private MarginView view(User user, Story s) {
        Book book = s.getBook();
        return new MarginView(s.getId(), book == null ? null : book.getId(), book == null ? null : book.getTitle(),
                s.getQuote(), s.getQuoteSource(), s.getText(), s.getRevision(), QuestionLog.parse(s.getAiLog()),
                s.getAiUpdatedAt(), aiService.remainingToday(user), writingService.aiConsent(user),
                s.getExportedAt() != null, s.getCreatedAt(), s.getUpdatedAt());
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

    // ── DTO (엔티티 직렬화 금지 — 평탄 record 화이트리스트 · 필드 이름이 앱 types.ts의 단일 출처) ──────────

    /** @param bookId 독서 서재 책 id — 없으면 책 없는 쪽지(C9) · 공부 서재 id는 404 */
    public record CreateMargin(Long bookId, String quote, String text) {}

    /**
     * @param revision 클라가 마지막으로 받은 판 — 서버 값과 다르면 409
     * @param exported 공유 시트가 「보냄」을 돌려줄 때만 true(C8). 래퍼 Boolean — 빠진 요청이 실패하지 않게
     */
    public record EditMargin(String quote, String text, int revision, Boolean exported) {}

    /** 목록 한 줄 — 생각 본문 없이 미리보기만(쪽지가 1만 자까지라 목록이 무거워지지 않게). @param preview 첫 비공백 줄 80자 */
    public record MarginSummary(Long id, Long bookId, String quote, String preview, int chars, boolean hasAi,
                                boolean exported, Instant createdAt, Instant updatedAt) {
        static MarginSummary from(Story s) {
            String line = s.getText().lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
            return new MarginSummary(s.getId(), s.getBook() == null ? null : s.getBook().getId(), s.getQuote(),
                    StoryService.cut(line, 80), s.getText().length(), s.getAiLog() != null,
                    s.getExportedAt() != null, s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    /**
     * 쪽지 한 장(생각 포함).
     *
     * @param quoteSource      옮긴 문장의 『제목』 · 저자 스냅샷 — 책을 지워도 남는다(J84)
     * @param questions        받은 질문 기록 — 없으면 rounds = []
     * @param aiRemainingToday 오늘 남은 글쓰기 AI 몫({@code Kind.WRITE})
     * @param exported         한 번이라도 내보냈나(C8)
     */
    public record MarginView(Long id, Long bookId, String bookTitle, String quote, String quoteSource, String text,
                             int revision, QuestionLog questions, Instant aiUpdatedAt, int aiRemainingToday,
                             boolean aiConsent, boolean exported, Instant createdAt, Instant updatedAt) {}
}
