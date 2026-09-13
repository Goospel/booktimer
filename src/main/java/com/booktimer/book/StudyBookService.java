package com.booktimer.book;

import com.booktimer.session.StudySessionRepository;
import com.booktimer.study.StudyNoteRepository;
import com.booktimer.study.StudyPlanItemRepository;
import com.booktimer.study.StudyRecallRepository;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * 공부 서재 유스케이스 — 목록, 등록, 회독 수 변경, 삭제.
 *
 * <p>검색은 도메인 중립이라 독서와 같은 문({@code GET /api/books/search})을 그대로 재사용한다 —
 * 여기엔 검색이 없다. 조회/변경/삭제는 소유권을 강제하고(IDOR 방지) {@link StudyBookRepository}로 영속한다.
 *
 * <p>삭제는 {@link BookService#delete}와 같은 모양이 됐다 — 공부 세션이 이 책을 가리키므로
 * ({@code study_session.book_id}) 참조를 먼저 풀어야 한다. 여백 글은 공부 모드에 없어 그 한 줄만 다르다.
 */
@Service
@Transactional
public class StudyBookService {

    private final StudyBookRepository studyBookRepository;
    private final StudySessionRepository studySessionRepository;
    private final StudyPlanItemRepository studyPlanItemRepository;
    private final StudyRecallRepository studyRecallRepository;
    private final StudyNoteRepository studyNoteRepository;

    public StudyBookService(StudyBookRepository studyBookRepository,
                            StudySessionRepository studySessionRepository,
                            StudyPlanItemRepository studyPlanItemRepository,
                            StudyRecallRepository studyRecallRepository,
                            StudyNoteRepository studyNoteRepository) {
        this.studyBookRepository = studyBookRepository;
        this.studySessionRepository = studySessionRepository;
        this.studyPlanItemRepository = studyPlanItemRepository;
        this.studyRecallRepository = studyRecallRepository;
        this.studyNoteRepository = studyNoteRepository;
    }

    @Transactional(readOnly = true)
    public List<StudyBook> myBooks(User user) {
        return studyBookRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * 검색 결과 한 행을 공부 서재에 담는다 — 언제나 0독으로 시작한다(상태 선택 시트가 없는 이유).
     *
     * <p>이미 담은 책(같은 user+isbn13)이면 새 행을 만들지 않고 기존 책을 돌려준다(멱등).
     * <b>회독 수는 보존한다</b> — 「추가」가 4독짜리 책을 0독으로 리셋하면 안 된다. isbn이 없는 결과는
     * 동일성 키가 없어 가드 미적용(여러 권 허용) — 독서 {@link BookService#addFromSearch}와 같은 규약.
     */
    public StudyBook add(User user, BookSearchResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        String isbn = Isbn.normalize(result.isbn13());
        if (isbn != null) {
            Optional<StudyBook> existing = studyBookRepository.findFirstByUserAndIsbn13(user, isbn);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        // category·pubDate는 받지 않는다 — 책BTI(독서 성향 분석) 입력이라 공부엔 소비처가 없다.
        StudyBook book = StudyBook.register(user, result.title(), result.author(), result.isbn13(),
                result.coverUrl(), result.publisher(), result.purchaseLink());
        return studyBookRepository.save(book);
    }

    /**
     * 내 공부 책의 회독 수를 <b>절대값으로</b> 설정한다. 소유권을 강제한다(IDOR 방지).
     *
     * @throws IllegalArgumentException 내 책이 아니거나 존재하지 않는 경우 / 회독 수가 음수인 경우
     */
    public StudyBook changeReadCount(User user, Long bookId, int readCount) {
        StudyBook book = ownedBook(user, bookId);
        book.changeReadCount(readCount);
        return studyBookRepository.save(book);
    }

    /**
     * 내 공부 책을 서재에서 지운다. 그 책으로 잰 세션은 <b>「책 미지정」으로 풀어</b>(book_id = null)
     * 공부 시간을 보존한다 — 책을 서재에서 빼도 그날 공부한 시간(당일 합·달력)은 남아야 하고,
     * {@code study_session.book_id} FK 때문에 이 정리 없이는 삭제가 제약 위반으로 실패한다.
     *
     * <p><b>필기(study_note)만은 예외로 삭제를 막는다</b> — 그쪽은 {@code book_id}가 NOT NULL이라 풀
     * 자리가 없고, 풀 수 있었더라도 책 없는 필기는 어느 목록에도 안 뜨고 백지복습 채점의 정답지에도 영영
     * 안 들어가는 <b>조용한 누락</b>이 된다. 미니앱에서 지우면 경고 없이 사라진다는 점이 결정적이었다
     * (미니앱은 필기의 존재를 모른다). 409 문구가 다음 행동을 말한다.
     *
     * @throws IllegalArgumentException 내 책이 아니거나 존재하지 않는 경우
     * @throws ResponseStatusException  409 — 그 책에 필기가 남아 있는 경우(아무것도 지우지 않는다)
     */
    public void delete(User user, Long bookId) {
        StudyBook book = ownedBook(user, bookId);
        long notes = studyNoteRepository.countByBook(book);
        if (notes > 0) {
            // 참조를 풀기 **전에** 막는다 — 던지고 나서 롤백에 기대면, 이 메서드가 언젠가 자기 트랜잭션
            // 밖에서 불릴 때 세션·일정만 풀린 반쪽 상태가 남는다.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "필기 " + notes + "장이 있는 책이에요. 필기를 먼저 지운 뒤 서재에서 빼 주세요.");
        }
        studySessionRepository.unlinkBook(book);
        // 일정도 같은 규칙으로 푼다(study_plan_item.book_id FK) — 「그날 뭘 하기로 했었나」는 남아야 하고,
        // subject 스냅샷이 제목을 대신 든다. 빠뜨리면 그 책을 쓴 사용자는 삭제 자체가 제약 위반으로 실패한다.
        studyPlanItemRepository.unlinkBook(book);
        // 백지복습도 같은 규칙 — 글은 남고 책 참조만 푼다(study_recall.book_id FK).
        studyRecallRepository.unlinkBook(book);
        studyBookRepository.delete(book);
    }

    /** 내 책일 때만 반환한다. 아니면(존재 안 함/남의 책) 거부 — 존재 여부도 노출하지 않는다(IDOR 방지). */
    private StudyBook ownedBook(User user, Long bookId) {
        return studyBookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new IllegalArgumentException("study book not found: " + bookId));
    }
}
