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
    public List<BookSearchResult> search(String query) {
        return searchClient.search(query);
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

    /** 내 책일 때만 반환한다. 아니면(존재 안 함/남의 책) 거부 — 존재 여부도 노출하지 않는다(IDOR 방지). */
    private Book ownedBook(User user, Long bookId) {
        return bookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new IllegalArgumentException("book not found: " + bookId));
    }
}
