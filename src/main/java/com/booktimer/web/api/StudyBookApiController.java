package com.booktimer.web.api;

import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookService;
import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;

/**
 * 공부 서재 JSON API — 미니앱 「공부」 모드의 서재 탭이 쓰는 문이다.
 *
 * <p>독서 서재({@code /api/books})와 <b>다른 문</b>인 것이 요구 그 자체다(V81 주석) — 두 서재가 섞이지
 * 않는다. 대신 <b>에러 계약은 글자 그대로 같다</b>: IDOR·미존재는 404로 통일하고(존재 비노출),
 * 도메인 규칙 위반(음수 회독)은 400이다.
 *
 * <p>책 <b>검색</b>은 여기 없다 — {@code GET /api/books/search}(알라딘 프록시)는 도메인 중립이라
 * 공부 화면이 그대로 재사용한다. 다만 그 응답의 {@code owned}는 독서 책장 기준이므로 공부 화면은
 * 무시하고 이 서재의 isbn 집합으로 다시 계산한다.
 *
 * <p>인증 라우팅은 설정 변경이 필요 없다 — {@code SecurityConfig.isMiniappApiRequest}가 Bearer 헤더 붙은
 * {@code /api/**}를 미니앱 체인으로 보내므로 {@code /api/study/books}가 자동으로 커버된다.
 */
@RestController
public class StudyBookApiController {

    private final CurrentUserService currentUserService;
    private final StudyBookService studyBookService;
    private final BookService bookService;

    public StudyBookApiController(CurrentUserService currentUserService,
                                  StudyBookService studyBookService,
                                  BookService bookService) {
        this.currentUserService = currentUserService;
        this.studyBookService = studyBookService;
        this.bookService = bookService;
    }

    /**
     * 내 공부 서재 전체(최신 등록 먼저).
     *
     * <p>{@code searchEnabled}를 함께 싣는 이유는 독서 {@code ShelfResponse}와 같다 — 검색 제공자가
     * 꺼져 있으면 화면이 「책 추가」 진입 자체를 그리지 않아야 한다. 값은 같은 출처를 재사용한다.
     */
    @GetMapping("/api/study/books")
    public StudyShelfResponse shelf(Principal principal) {
        User user = currentUserService.resolve(principal);
        List<StudyBookRow> rows = studyBookService.myBooks(user).stream().map(StudyBookRow::from).toList();
        return new StudyShelfResponse(bookService.searchEnabled(), rows);
    }

    /**
     * 검색 결과 한 행을 공부 서재에 담는다 — 0독으로 시작한다.
     * 같은 isbn을 다시 담으면 기존 행이 그대로 돌아온다(회독 수 보존).
     */
    @PostMapping("/api/study/books")
    public ResponseEntity<StudyBookRow> add(@RequestBody AddRequest req, Principal principal) {
        User user = currentUserService.resolve(principal);
        BookSearchResult result = new BookSearchResult(req.title(), req.author(), req.isbn13(),
                req.coverUrl(), req.publisher(), req.purchaseLink());
        try {
            return ResponseEntity.ok(StudyBookRow.from(studyBookService.add(user, result)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "책을 추가할 수 없습니다");
        }
    }

    /**
     * 회독 수를 <b>절대값으로</b> 설정한다(클라가 현재값 ±1을 보낸다) — 멱등이라 연타·재시도에 안전하다.
     *
     * <p>음수 검사를 <b>소유권 조회보다 먼저</b> 한다: 잘못된 값은 어느 책이든 400이라, 남의 책 id로
     * 400/404를 갈라 존재 여부를 캐낼 창이 열리지 않는다.
     *
     * @return 200 갱신된 행 / 400 음수 / 404 내 책이 아니거나 없음
     */
    @PostMapping("/api/study/books/{id}/read-count")
    public ResponseEntity<StudyBookRow> setReadCount(@PathVariable Long id,
                                                     @RequestBody ReadCountRequest req,
                                                     Principal principal) {
        User user = currentUserService.resolve(principal);
        if (req.readCount() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회독 수는 0보다 작을 수 없습니다");
        }
        return ResponseEntity.ok(StudyBookRow.from(
                mutate(() -> studyBookService.changeReadCount(user, id, req.readCount()))));
    }

    @PostMapping("/api/study/books/{id}/delete")
    public ResponseEntity<DeleteResult> delete(@PathVariable Long id, Principal principal) {
        User user = currentUserService.resolve(principal);
        mutate(() -> { studyBookService.delete(user, id); return null; });
        return ResponseEntity.ok(new DeleteResult(true));
    }

    /** IDOR/없는 책 IAE → 404(존재 비노출). 독서 {@code BookApiController.mutate}와 같은 계약. */
    private static <T> T mutate(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다");
        }
    }

    // ── DTO (엔티티 직렬화 금지 — 평탄 record 화이트리스트) ───────────────────

    /**
     * 공부 서재 한 행. 독서 {@code MyBookSummary}보다 <b>훨씬 좁다</b> — 상태·공개범위·누적 시간·여백 글 수는
     * 공부 화면에 소비처가 없다. 대신 이 화면의 유일한 분류 축인 {@code readCount}가 있다.
     */
    public record StudyBookRow(Long id, String title, String author, String coverUrl, String isbn13,
                               int readCount, String purchaseLink) {
        static StudyBookRow from(StudyBook b) {
            return new StudyBookRow(b.getId(), b.getTitle(), b.getAuthor(), b.getCoverUrl(), b.getIsbn13(),
                    b.getReadCount(), b.getPurchaseLink());
        }
    }

    /** @param searchEnabled 검색 제공자 가동 여부 — 꺼져 있으면 화면이 「책 추가」 진입을 그리지 않는다 */
    public record StudyShelfResponse(boolean searchEnabled, List<StudyBookRow> books) {}

    /** 검색 결과 행을 그대로 되돌려받는 모양 — status·category·pubDate가 없는 것이 독서와의 차이다. */
    public record AddRequest(String title, String author, String isbn13, String coverUrl,
                             String publisher, String purchaseLink) {}

    /** @param readCount 설정할 회독 수(0 이상 — 0은 「아직 안 돌았다」) */
    public record ReadCountRequest(int readCount) {}

    public record DeleteResult(boolean deleted) {}
}
