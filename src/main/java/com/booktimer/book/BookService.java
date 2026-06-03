package com.booktimer.book;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public BookService(BookRepository bookRepository, BookSearchClient searchClient) {
        this.bookRepository = bookRepository;
        this.searchClient = searchClient;
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

    public Book changeStatus(User user, Long bookId, BookStatus status) {
        Book book = ownedBook(user, bookId);
        book.changeStatus(status);
        return bookRepository.save(book);
    }

    public void delete(User user, Long bookId) {
        Book book = ownedBook(user, bookId);
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
