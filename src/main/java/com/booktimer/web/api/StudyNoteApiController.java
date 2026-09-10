package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.security.CurrentUserService;
import com.booktimer.study.StudyNote;
import com.booktimer.study.StudyNoteService;
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
import java.time.Instant;
import java.util.List;

/**
 * 공부 필기의 문 — 목록 · 조회 · 생성 · 갱신 · 삭제.
 *
 * <p>에러 계약은 {@link StudyRecallApiController}와 같다: IAE 문구가 그대로 400 본문, 없는·남의 것은
 * 404(존재 비노출), {@link ResponseStatusException}의 사유는 평문 본문(409).
 *
 * <p><b>404를 여기서 가로채지 않는다</b> — {@code StudyNoteService.owned}가 그 자리에서 던진다. 예전엔
 * 이 클래스가 모든 IAE를 404로 옮겼는데, 그러면 같은 갈래로 오는 <b>입력 검증 IAE(빈 본문·길이 초과)까지
 * 404로 뭉개져</b> 생성은 400·갱신은 404라는 어긋남이 생겼다(설계 §3.4 위반). 소유권 판정이 검증보다
 * <b>먼저</b>인 것도 계약이다 — 뒤집으면 400/404 차이로 남의 필기의 존재를 캐낼 수 있다.
 *
 * <p>{@link #ownedBook}이 백지복습의 {@code ownedBookOrNull}과 <b>다르다</b> — 거기선 {@code null}이
 * 「책 없이」라는 정당한 선택이지만, 필기는 책이 필수라 {@code null}이 400이다.
 *
 * <p>목록이 본문을 싣지 않는 것도 계약의 일부다({@link NoteRow}) — 한 책에 수백 장이 쌓여도 목록 왕복은
 * 행당 100B쯤이고, 본문은 고를 때 한 장씩 가져온다. 그래서 페이징이 없다.
 */
@RestController
public class StudyNoteApiController {

    private final CurrentUserService currentUserService;
    private final StudyNoteService noteService;
    private final StudyBookRepository studyBookRepository;

    public StudyNoteApiController(CurrentUserService currentUserService,
                                  StudyNoteService noteService,
                                  StudyBookRepository studyBookRepository) {
        this.currentUserService = currentUserService;
        this.noteService = noteService;
        this.studyBookRepository = studyBookRepository;
    }

    /**
     * 그 책의 필기 목록(최근 고친 순, 본문 없음).
     *
     * @return 200 {@link NoteListResponse} / 400 bookId 없음 / 404 남의 책·없는 책
     */
    @GetMapping("/api/study/notes")
    public NoteListResponse list(Principal principal, @RequestParam(name = "bookId", required = false) Long bookId) {
        User user = currentUserService.resolve(principal);
        StudyBook book = ownedBook(user, bookId);
        return new NoteListResponse(noteService.list(user, book).stream().map(NoteRow::from).toList());
    }

    /**
     * 필기 한 장(본문 포함).
     *
     * @return 200 {@link NoteResponse} / 404 없거나 남의 것
     */
    @GetMapping("/api/study/notes/{id}")
    public NoteResponse get(Principal principal, @PathVariable Long id) {
        User user = currentUserService.resolve(principal);
        return noteService.find(user, id)
                .map(NoteResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "필기를 찾을 수 없습니다"));
    }

    /**
     * 새 필기 한 장 — 화면은 <b>첫 비공백 본문</b>에서만 이 문을 부른다(빈 초안이 행으로 쌓이지 않게).
     *
     * @return 200 {@link NoteResponse} / 400 책 없음·빈 본문·길이 / 404 남의 책
     */
    @PostMapping("/api/study/notes")
    public NoteResponse create(Principal principal, @RequestBody CreateRequest request) {
        User user = currentUserService.resolve(principal);
        StudyBook book = ownedBook(user, request.bookId());
        return NoteResponse.from(noteService.create(user, book, request.title(), request.body()));
    }

    /**
     * 필기를 고친다 — 자동저장이 1.5초마다 두드리는 문이다.
     *
     * @return 200 {@link NoteResponse}(revision +1) / 400 빈 본문·길이 / 404 없거나 남의 것 /
     *         409 그 사이 다른 곳에서 고쳐짐(덮어쓰지 않는다)
     */
    @PostMapping("/api/study/notes/{id}")
    public NoteResponse update(Principal principal, @PathVariable Long id, @RequestBody UpdateRequest request) {
        User user = currentUserService.resolve(principal);
        return NoteResponse.from(
                noteService.update(user, id, request.title(), request.body(), request.revision()));
    }

    /** @return 200 {@code {deleted:true}} / 404 없거나 남의 것 */
    @PostMapping("/api/study/notes/{id}/delete")
    public DeleteResult delete(Principal principal, @PathVariable Long id) {
        User user = currentUserService.resolve(principal);
        noteService.delete(user, id);
        return new DeleteResult(true);
    }

    /** IAE 메시지가 그대로 400 본문이 되어 화면 상태줄에 뜬다({@link StudyRecallApiController}와 같은 규약). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * 409의 <b>한국어 사유를 본문으로</b> — 없으면 전역 처리기가 {@code error.html}을 렌더해 화면이
     * 「다른 곳에서 고쳐졌어요」 대신 {@code <!DOCTYPE html>…}을 받는다(그러면 클라가 그 응답을 불신해
     * 폴백 문구를 띄우고, 사용자는 무엇을 해야 하는지 영영 못 읽는다).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }

    /**
     * 내 공부 책일 때만 — {@code null}이면 400, 남의 것·없는 것이면 404(존재 비노출).
     *
     * <p>필기는 책이 필수라 {@code null}이 정당한 선택이 아니다. 400 문구가 곧 화면 안내다.
     */
    private StudyBook ownedBook(User user, Long bookId) {
        if (bookId == null) {
            throw new IllegalArgumentException("책을 골라 주세요");
        }
        return studyBookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다"));
    }

    // ── DTO (엔티티 직렬화 금지 — 평탄 record 화이트리스트) ───────────────────

    /**
     * @param bookId 필기를 걸 공부 책 — <b>필수</b>다(null이면 400)
     * @param title  선택. 공백이면 저장 시 null이 되고 화면이 본문에서 라벨을 파생한다
     */
    public record CreateRequest(Long bookId, String title, String body) {}

    /** @param revision 클라가 마지막으로 받은 판 번호 — 서버 값과 다르면 409 */
    public record UpdateRequest(String title, String body, int revision) {}

    public record DeleteResult(boolean deleted) {}

    /**
     * 목록 한 행 — <b>본문이 없다</b>. 목록은 라벨과 크기만 필요하고, 본문까지 실으면 수백 장짜리 책의
     * 목록 왕복이 메가바이트가 된다.
     *
     * @param chars 본문 글자 수 — 화면의 「N자」이자 정답지 상한(§NoteReference)의 단위
     */
    public record NoteRow(Long id, String title, int chars, Instant updatedAt) {
        static NoteRow from(StudyNote note) {
            return new NoteRow(note.getId(), note.getTitle(), note.chars(), note.getUpdatedAt());
        }
    }

    public record NoteListResponse(List<NoteRow> notes) {}

    /** @param revision 다음 갱신 요청에 그대로 되실어야 하는 판 번호 */
    public record NoteResponse(Long id, Long bookId, String title, String body,
                               int revision, Instant updatedAt) {
        static NoteResponse from(StudyNote note) {
            return new NoteResponse(note.getId(), note.getBook().getId(), note.getTitle(),
                    note.getBody(), note.getRevision(), note.getUpdatedAt());
        }
    }
}
