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
    private final CoupangLinkBuilder coupangLinkBuilder;

    public BookService(BookRepository bookRepository, BookSearchClient searchClient,
                       ReadingSessionRepository sessionRepository,
                       CoupangLinkBuilder coupangLinkBuilder) {
        this.bookRepository = bookRepository;
        this.searchClient = searchClient;
        this.sessionRepository = sessionRepository;
        this.coupangLinkBuilder = coupangLinkBuilder;
    }

    @Transactional(readOnly = true)
    public boolean searchEnabled() {
        return searchClient.isEnabled();
    }

    /** 쿠팡 파트너스 링크 노출 여부(추적코드 설정 시 true) — {@link #searchEnabled()}와 대칭. */
    @Transactional(readOnly = true)
    public boolean coupangEnabled() {
        return coupangLinkBuilder.isEnabled();
    }

    @Transactional(readOnly = true)
    public BookSearchPage search(String query, BookSearchType type, int page) {
        BookSearchPage raw = searchClient.search(query, type, page);
        return filterToSearchType(raw, query, type);
    }

    /**
     * 제공자(알라딘)의 {@code QueryType=Title}/{@code Author}가 문서와 달리 다른 필드 매칭을 섞어
     * 돌려주는 경우를 방어한다 — 사용자가 고른 기준 필드(제목/저자)에 검색어가 실제로 든 결과만 남긴다.
     * (예: "모기"를 제목으로 검색했는데 저자 "모기 겐이치로" 책이 끼어드는 것을 거른다.)
     *
     * <p>공백·대소문자 차이는 무시한다(정규화 후 contains) — "Clean Code"↔"cleancode" 같은 차이로
     * 정상 결과가 누락되지 않게. 검색어가 비었거나 기준이 없으면 원본을 그대로 둔다.
     */
    private static BookSearchPage filterToSearchType(BookSearchPage raw, String query, BookSearchType type) {
        if (raw == null || type == null || query == null) {
            return raw;
        }
        String needle = normalize(query);
        if (needle.isEmpty()) {
            return raw;
        }
        List<BookSearchResult> filtered = raw.results().stream()
                .filter(r -> {
                    String field = (type == BookSearchType.AUTHOR) ? r.author() : r.title();
                    return field != null && normalize(field).contains(needle);
                })
                .toList();
        return new BookSearchPage(filtered, raw.page(), raw.pageSize(), raw.totalResults());
    }

    /** 매칭 비교용 정규화 — 모든 공백 제거 + 소문자(로케일 무관). */
    private static String normalize(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    public Book addFromSearch(User user, BookSearchResult result, BookStatus status) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        // 이미 가진 책(같은 user+isbn13)이면 새 행을 만들지 않고 기존 책을 반환한다(멱등) — UI는 #273이 추가 폼을
        // 숨기지만 직접 POST 경로로 중복 행이 생기는 풋건을 서버에서도 막는다. 상태(status)는 보존한다("추가"가
        // 기존 상태를 몰래 바꾸지 않게). isbn 없는 수동 풍 결과는 동일성 키가 없어 가드 미적용(여러 권 허용).
        String isbn = Isbn.normalize(result.isbn13());
        if (isbn != null) {
            Optional<Book> existing = bookRepository.findFirstByUserAndIsbn13(user, isbn);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        // 카탈로그 메타(장르·출간일)까지 적재 — 책BTI 입력. 수동 경로(addManual)는 메타가 없어 8-인자 register.
        Book book = Book.register(user, result.title(), result.author(), result.isbn13(),
                result.coverUrl(), result.publisher(), result.purchaseLink(),
                result.category(), result.pubDate(), status);
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
     * 내 책의 공개 범위(공개/비공개)를 바꾼다(SNS). 소유권을 강제한다(IDOR 방지) —
     * 남의 책 공개 여부는 건드릴 수 없다.
     *
     * @throws IllegalArgumentException 내 책이 아니거나 존재하지 않는 경우
     */
    public Book setVisibility(User user, Long bookId, BookVisibility visibility) {
        Book book = ownedBook(user, bookId);
        book.changeVisibility(visibility);
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

    /**
     * 공개(PUBLIC) 책의 "구매" 클릭을 집계하고 이동할 제휴 구매링크를 돌려준다 — <b>남의 책방(공개 프로필)에서</b> 쓴다.
     *
     * <p>{@link #recordPurchaseClick}(내 책 전용, 소유권 강제)와 달리 소유권 대신 <b>공개 여부</b>를 게이트로 둔다:
     * 공개 책은 이미 누구나 프로필에서 보는 것이라 구매링크를 함께 노출해도 새 위험이 없다. 반대로 비공개·없는 책은
     * 임의 id로 비공개 책의 구매링크/존재를 캐낼 수 없게 거부한다(null 반환).
     *
     * <p><b>클릭을 왜 "책 주인 행"에 집계하나</b>(사용자 결정 2026-06-06의 근거): 이 카운트({@link Book#clickCount})의
     * 목적은 <i>"어떤 책이 구매 의향을 내나"</i>를 보는 <b>제휴 수익 분석</b>이다(알라딘 3% 제휴가 현 유일 수익 토대).
     * 카운트는 <b>행(사용자별 책 1권) 단위로 적립</b>되고 실제 질문은 ISBN으로 롤업해 답하므로, '주인'은 그저 화면에
     * 떠 있던 그 행일 뿐 — <b>보상·귀속의 의미는 없다</b>(주인에게 수수료를 나눠주는 모델이 없는 한). 그래서 세 선택지 중
     * "집계 안 함"이 가장 큰 손실이다: 남의 책방을 보다 생긴 구매 의향은 소셜 커머스의 핵심 신호라 버리면 안 된다.
     * 'viewer 귀속'은 "누가 잘 누르나"라는 다른 질문이라 지금은 불필요. <b>재검토 트리거</b>: 책방 주인/추천자에게 제휴
     * 수수료를 정산하는 보상 모델을 들이면 그때 viewer/owner 귀속을 분리해야 한다(클릭이 곧 돈이 되므로).
     * 현 시점엔 이 카운트를 <b>읽는 소비처가 아직 없다</b>(write-only로 적립만) — 결정의 실효 위험이 낮은 이유.
     *
     * @return 이동할 구매링크. 비공개·존재하지 않음·링크 없음이면 null.
     */
    public String recordPublicPurchaseClick(Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null || !book.isPublic()) {
            return null; // 없거나 비공개 — 존재/링크 누설 없이 거부
        }
        String link = book.getPurchaseLink();
        if (link == null || link.isBlank()) {
            return null;
        }
        book.recordPurchaseClick();
        bookRepository.save(book);
        return link;
    }

    /**
     * 내 책의 쿠팡 "구매" 클릭을 집계하고 이동할 쿠팡 검색 링크를 돌려준다 — 알라딘 {@link #recordPurchaseClick}과 대칭.
     *
     * <p>소유권을 강제한다(IDOR 방지). 링크는 DB에 없고 {@link CoupangLinkBuilder}가 런타임 생성한다 —
     * 쿠팡은 제목이 항상 있어 링크가 늘 만들어지므로, null 사유는 "비활성(추적코드 미설정)"뿐이다(그땐 무집계).
     *
     * @return 이동할 쿠팡 검색 링크. 비활성이면 null.
     * @throws IllegalArgumentException 내 책이 아니거나 존재하지 않는 경우
     */
    public String recordCoupangClick(User user, Long bookId) {
        Book book = ownedBook(user, bookId);
        String link = coupangLinkBuilder.buildSearchLink(book);
        if (link == null) {
            return null; // 비활성(추적코드 미설정) — 집계하지 않음
        }
        book.recordCoupangClick();
        bookRepository.save(book);
        return link;
    }

    /**
     * 공개(PUBLIC) 책의 쿠팡 "구매" 클릭을 집계하고 링크를 돌려준다 — 남의 책방(공개 프로필)에서 쓴다.
     * {@link #recordPublicPurchaseClick}(알라딘)과 같은 정신: 소유권 대신 공개 여부를 게이트로 두고,
     * 클릭은 책 주인 행에 집계한다(2026-06-06 결정의 근거는 알라딘 메서드 JavaDoc 참조).
     *
     * @return 이동할 쿠팡 검색 링크. 비공개·존재하지 않음·비활성이면 null.
     */
    public String recordPublicCoupangClick(Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null || !book.isPublic()) {
            return null; // 없거나 비공개 — 존재 누설 없이 거부
        }
        String link = coupangLinkBuilder.buildSearchLink(book);
        if (link == null) {
            return null; // 비활성(추적코드 미설정)
        }
        book.recordCoupangClick();
        bookRepository.save(book);
        return link;
    }

    /** 내 책일 때만 반환한다. 아니면(존재 안 함/남의 책) 거부 — 존재 여부도 노출하지 않는다(IDOR 방지). */
    private Book ownedBook(User user, Long bookId) {
        return bookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new IllegalArgumentException("book not found: " + bookId));
    }
}
