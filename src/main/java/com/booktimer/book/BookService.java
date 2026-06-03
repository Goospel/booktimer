package com.booktimer.book;

import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 책장 유스케이스 — 검색, 등록, 목록, 상태 변경, 삭제.
 *
 * <p>검색은 {@link BookSearchClient}(포트)에 위임하고, 등록/조회/변경/삭제는 소유권을
 * 강제하며(IDOR 방지) {@link BookRepository}로 영속한다.
 */
@Service
@Transactional
public class BookService {

    private final BookRepository bookRepository;
    private final BookSearchClient searchClient;
    private final ReadingSessionRepository sessionRepository;

    public BookService(BookRepository bookRepository, BookSearchClient searchClient,
                       ReadingSessionRepository sessionRepository) {
        this.bookRepository = bookRepository;
        this.searchClient = searchClient;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public boolean searchEnabled() {
        return searchClient.isEnabled();
    }

    @Transactional(readOnly = true)
    public BookSearchPage search(String query, int page) {
        return searchClient.search(query, page);
    }

    public Book addFromSearch(User user, BookSearchResult result, BookStatus status) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        Book book = Book.register(user, result.title(), result.author(), result.isbn13(),
                result.coverUrl(), result.publisher(), result.purchaseLink(), status);
        return bookRepository.save(book);
    }

    public Book addManual(User user, String title, String author, BookStatus status) {
        Book book = Book.register(user, title, author, null, null, null, null, status);
        return bookRepository.save(book);
    }

    @Transactional(readOnly = true)
    public List<Book> myBooks(User user) {
        return bookRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /** 내 책 한 권을 조회한다(소유권 검사 — 남의 책/없는 책이면 비어 있음). 상세 화면 등 읽기 경로용. */
    @Transactional(readOnly = true)
    public Optional<Book> findMyBook(User user, Long bookId) {
        return bookRepository.findByIdAndUser(bookId, user);
    }

    public Book changeStatus(User user, Long bookId, BookStatus status) {
        Book book = ownedBook(user, bookId);
        book.changeStatus(status);
        return bookRepository.save(book);
    }

    /**
     * 내 책을 책장에서 삭제한다. 그 책을 가리키던 측정 세션은 "책 미지정"으로 풀어(book_id = null)
     * 독서 기록(잔디·누적 시간)을 보존한다 — {@code reading_session.book_id} FK 때문에 이 정리 없이는
     * 삭제가 제약 위반으로 실패한다(AccountService가 탈퇴 시 FK 순서로 정리하는 것과 같은 이유).
     */
    public void delete(User user, Long bookId) {
        Book book = ownedBook(user, bookId);
        sessionRepository.unlinkBook(book);
        bookRepository.delete(book);
    }

    /**
     * 내 책의 "구매" 클릭을 집계하고 이동할 제휴 구매링크를 돌려준다.
     *
     * <p>소유권을 강제한다(IDOR 방지) — 남의 책이면 거부. 구매링크가 없는 책(수동 등록 등)은
     * 갈 곳이 없으므로 집계하지 않고 null을 돌려준다(호출자는 책장으로 돌려보낸다).
     *
     * @return 이동할 구매링크. 링크가 없으면 null.
     * @throws IllegalArgumentException 내 책이 아니거나 존재하지 않는 경우
     */
    public String recordPurchaseClick(User user, Long bookId) {
        Book book = ownedBook(user, bookId);
        String link = book.getPurchaseLink();
        if (link == null || link.isBlank()) {
            return null;
        }
        book.recordPurchaseClick();
        bookRepository.save(book);
        return link;
    }

    /** 내 책일 때만 반환한다. 아니면(존재 안 함/남의 책) 거부 — 존재 여부도 노출하지 않는다(IDOR 방지). */
    private Book ownedBook(User user, Long bookId) {
        return bookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new IllegalArgumentException("book not found: " + bookId));
    }
}
