package com.booktimer.book;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 공부 서재 유스케이스 — 목록, 등록, 회독 수 변경, 삭제.
 *
 * <p>검색은 도메인 중립이라 독서와 같은 문({@code GET /api/books/search})을 그대로 재사용한다 —
 * 여기엔 검색이 없다. 조회/변경/삭제는 소유권을 강제하고(IDOR 방지) {@link StudyBookRepository}로 영속한다.
 *
 * <p>{@link BookService#delete}와 달리 삭제가 <b>단순 delete 한 줄</b>이다: 공부 책엔 딸린 자식이 없다
 * (세션이 가리키지 않고 — 타이머-책 연결은 이번 범위 밖 — 여백 글도 공부 모드엔 없다).
 */
@Service
@Transactional
public class StudyBookService {

    private final StudyBookRepository studyBookRepository;

    public StudyBookService(StudyBookRepository studyBookRepository) {
        this.studyBookRepository = studyBookRepository;
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
     * 내 공부 책을 서재에서 지운다. 딸린 자식이 없어 정리 단계가 필요 없다(위 클래스 주석).
     *
     * @throws IllegalArgumentException 내 책이 아니거나 존재하지 않는 경우
     */
    public void delete(User user, Long bookId) {
        studyBookRepository.delete(ownedBook(user, bookId));
    }

    /** 내 책일 때만 반환한다. 아니면(존재 안 함/남의 책) 거부 — 존재 여부도 노출하지 않는다(IDOR 방지). */
    private StudyBook ownedBook(User user, Long bookId) {
        return studyBookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new IllegalArgumentException("study book not found: " + bookId));
    }
}
