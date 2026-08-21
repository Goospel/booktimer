package com.booktimer.book;

import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 책장 유스케이스 테스트 — 실제 레포(H2) + 가짜 검색 클라이언트(mock).
 *
 * <p>검색은 포트에 위임하므로 외부 API 없이 mock으로 검증하고, 등록/조회/상태변경/삭제는
 * 소유권(IDOR 방지)과 유저 격리를 본다.
 */
@SpringBootTest
@Transactional
class BookServiceTest {

    @Autowired
    private BookService bookService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ReadingSessionRepository sessionRepository;

    @MockitoBean
    private BookSearchClient searchClient;

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    /** 진짜 알라딘 링크 형식이어야 한다 — 구매 클릭 경로가 host를 검사해 외부 도메인을 버린다(AladinLink). */
    private static final String ALADIN_LINK = "https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=1&ttbkey=x";

    private static BookSearchResult cleanCode() {
        return new BookSearchResult("클린 코드", "로버트 마틴", "9788966260959",
                "http://cover/clean.jpg", "인사이트", ALADIN_LINK);
    }

    @Test
    @DisplayName("검색 결과로 책을 등록하면 메타데이터가 모두 저장된다")
    void addFromSearch_savesMetadata() {
        User u = newUser("a@booktimer.com");

        Book saved = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("클린 코드");
        assertThat(saved.getAuthor()).isEqualTo("로버트 마틴");
        assertThat(saved.getIsbn13()).isEqualTo("9788966260959");
        assertThat(saved.getCoverUrl()).isEqualTo("http://cover/clean.jpg");
        assertThat(saved.getPurchaseLink()).isEqualTo(ALADIN_LINK);
        assertThat(saved.getStatus()).isEqualTo(BookStatus.WANT_TO_READ);
    }

    @Test
    @DisplayName("검색 결과의 장르(category)·출간일(pubDate)도 책장에 적재된다 — 책BTI 입력")
    void addFromSearch_savesCatalogMetadata() {
        User u = newUser("meta@booktimer.com");
        BookSearchResult withMeta = new BookSearchResult(
                "한국소설책", "어떤작가", "9788900000001", null, "출판사", null,
                "국내도서>소설/시/희곡>한국소설", "2020-03-15");

        Book saved = bookService.addFromSearch(u, withMeta, BookStatus.WANT_TO_READ);

        assertThat(saved.getCategory()).isEqualTo("국내도서>소설/시/희곡>한국소설");
        assertThat(saved.getPubDate()).isEqualTo("2020-03-15");
    }

    @Test
    @DisplayName("같은 isbn 책을 또 추가해도 책장에 중복 행이 생기지 않는다(직접 POST 방어, 멱등)")
    void addFromSearch_duplicateIsbn_doesNotCreateSecondRow() {
        User u = newUser("dup@booktimer.com");

        Book first = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);
        Book again = bookService.addFromSearch(u, cleanCode(), BookStatus.READING);

        assertThat(again.getId()).isEqualTo(first.getId()); // 기존 책을 그대로 반환(멱등)
        assertThat(bookService.myBooks(u)).hasSize(1);
    }

    @Test
    @DisplayName("이미 가진 책을 다른 상태로 재추가해도 기존 상태를 보존한다('추가'가 상태를 몰래 바꾸지 않음)")
    void addFromSearch_duplicate_preservesExistingStatus() {
        User u = newUser("dupstatus@booktimer.com");

        bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);
        Book again = bookService.addFromSearch(u, cleanCode(), BookStatus.FINISHED);

        assertThat(again.getStatus()).isEqualTo(BookStatus.WANT_TO_READ); // 최초 상태 유지
    }

    @Test
    @DisplayName("isbn 없는 책은 가드가 적용되지 않아 여러 번 추가된다(null끼리 거짓 병합 방지)")
    void addFromSearch_nullIsbn_allowsMultiple() {
        User u = newUser("nullisbn@booktimer.com");
        BookSearchResult noIsbn = new BookSearchResult("제목만 있는 책", null, null, null, null, null);

        bookService.addFromSearch(u, noIsbn, BookStatus.WANT_TO_READ);
        bookService.addFromSearch(u, noIsbn, BookStatus.WANT_TO_READ);

        assertThat(bookService.myBooks(u)).hasSize(2); // isbn null은 동일성 키가 없어 면제
    }

    @Test
    @DisplayName("같은 isbn이라도 사용자가 다르면 각자 책장에 들어간다(가드는 user 범위)")
    void addFromSearch_sameIsbnDifferentUsers_bothKept() {
        User a = newUser("ua@booktimer.com");
        User b = newUser("ub@booktimer.com");

        Book ba = bookService.addFromSearch(a, cleanCode(), BookStatus.WANT_TO_READ);
        Book bb = bookService.addFromSearch(b, cleanCode(), BookStatus.WANT_TO_READ);

        assertThat(ba.getId()).isNotEqualTo(bb.getId());
        assertThat(bookService.myBooks(a)).hasSize(1);
        assertThat(bookService.myBooks(b)).hasSize(1);
    }

    @Test
    @DisplayName("수동 입력으로도 책을 등록할 수 있다(제목/저자/상태)")
    void addManual_savesMinimal() {
        User u = newUser("b@booktimer.com");

        Book saved = bookService.addManual(u, "이펙티브 자바", "조슈아 블로크", BookStatus.READING);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("이펙티브 자바");
        assertThat(saved.getAuthor()).isEqualTo("조슈아 블로크");
        assertThat(saved.getStatus()).isEqualTo(BookStatus.READING);
        assertThat(saved.getIsbn13()).isNull();
    }

    @Test
    @DisplayName("내 책장은 내 책만 보인다(유저 격리)")
    void myBooks_isolatedPerUser() {
        User me = newUser("me@booktimer.com");
        User other = newUser("other@booktimer.com");
        bookService.addManual(me, "내 책", null, BookStatus.READING);
        bookService.addManual(other, "남의 책", null, BookStatus.READING);

        List<Book> mine = bookService.myBooks(me);

        assertThat(mine).extracting(Book::getTitle).containsExactly("내 책");
    }

    @Test
    @DisplayName("상태 변경은 소유자만 가능하다")
    void changeStatus_byOwner() {
        User u = newUser("c@booktimer.com");
        Book book = bookService.addManual(u, "리팩터링", null, BookStatus.WANT_TO_READ);

        Book updated = bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);

        assertThat(updated.getStatus()).isEqualTo(BookStatus.FINISHED);
    }

    @Test
    @DisplayName("완독 상태로 수동 등록하면 완독 시각이 함께 기록된다(등록 경로 스탬프)")
    void addManual_finished_stampsFinishedAt() {
        User u = newUser("fin-add@booktimer.com");

        Book saved = bookService.addManual(u, "완독으로 등록한 책", null, BookStatus.FINISHED);

        assertThat(saved.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("완독 아닌 상태로 등록하면 완독 시각은 없다")
    void addManual_notFinished_finishedAtNull() {
        User u = newUser("fin-add2@booktimer.com");

        Book saved = bookService.addManual(u, "읽는 중 책", null, BookStatus.READING);

        assertThat(saved.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("검색 등록도 완독 상태면 완독 시각이 기록된다")
    void addFromSearch_finished_stampsFinishedAt() {
        User u = newUser("fin-search@booktimer.com");

        Book saved = bookService.addFromSearch(u, cleanCode(), BookStatus.FINISHED);

        assertThat(saved.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus: 완독 전환 시 완독 시각을 기록하고, 완독 해제 시 지운다")
    void changeStatus_maintainsFinishedAt() {
        User u = newUser("fin-chg@booktimer.com");
        Book book = bookService.addManual(u, "완독 시각 추적 책", null, BookStatus.READING);

        Book finished = bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);
        assertThat(finished.getFinishedAt()).isNotNull();

        Book reopened = bookService.changeStatus(u, book.getId(), BookStatus.READING);
        assertThat(reopened.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("남의 책 상태 변경은 거부된다(IDOR 방지)")
    void changeStatus_rejectsNonOwner() {
        User owner = newUser("owner@booktimer.com");
        User attacker = newUser("attacker@booktimer.com");
        Book book = bookService.addManual(owner, "도메인 주도 설계", null, BookStatus.READING);

        assertThatThrownBy(() -> bookService.changeStatus(attacker, book.getId(), BookStatus.FINISHED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("삭제는 소유자만 가능하고, 남의 책 삭제는 거부된다")
    void delete_ownershipEnforced() {
        User owner = newUser("o2@booktimer.com");
        User attacker = newUser("a2@booktimer.com");
        Book book = bookService.addManual(owner, "TCP/IP", null, BookStatus.READING);

        assertThatThrownBy(() -> bookService.delete(attacker, book.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        bookService.delete(owner, book.getId());
        assertThat(bookService.myBooks(owner)).isEmpty();
    }

    @Test
    @DisplayName("읽은 적 있는 책도 삭제된다 — 측정 세션은 '책 미지정'으로 남아 독서 기록이 보존된다")
    void delete_unlinksReadingSessions_keepsHistory() {
        User u = newUser("read@booktimer.com");
        Book book = bookService.addManual(u, "리눅스 커널 내부구조", null, BookStatus.READING);
        // 타이머로 30분 읽은 세션을 이 책에 연결한다 → reading_session.book_id 가 이 책을 가리킨다.
        ReadingSession session = ReadingSession.start(u, Instant.parse("2026-06-01T10:00:00Z"), book);
        session.end(Instant.parse("2026-06-01T10:30:00Z"));
        sessionRepository.save(session);

        bookService.delete(u, book.getId()); // FK 위반 없이 삭제되어야 한다

        assertThat(bookService.myBooks(u)).isEmpty();
        ReadingSession reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getBook()).isNull(); // 책 연결만 풀린다(세션은 남는다)
        assertThat(reloaded.getDurationSeconds()).isEqualTo(1800L); // 읽은 시간은 보존
    }

    @Test
    @DisplayName("구매 클릭: 내 책이고 링크가 있으면 카운트를 올리고 구매링크를 돌려준다")
    void recordPurchaseClick_ownedWithLink_countsAndReturnsLink() {
        User u = newUser("buy@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordPurchaseClick(u, book.getId());

        assertThat(link).isEqualTo(ALADIN_LINK);
        assertThat(bookService.myBooks(u).get(0).getClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("구매 클릭: 남의 책이면 거부된다(IDOR 방지)")
    void recordPurchaseClick_nonOwner_rejected() {
        User owner = newUser("o3@booktimer.com");
        User attacker = newUser("a3@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);

        assertThatThrownBy(() -> bookService.recordPurchaseClick(attacker, book.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bookService.myBooks(owner).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("구매 클릭: 구매링크가 없으면 null을 돌려주고 카운트를 올리지 않는다")
    void recordPurchaseClick_noLink_returnsNullNoCount() {
        User u = newUser("nolink@booktimer.com");
        Book book = bookService.addManual(u, "수동 책", null, BookStatus.READING); // 링크 없음

        String link = bookService.recordPurchaseClick(u, book.getId());

        assertThat(link).isNull();
        assertThat(bookService.myBooks(u).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 책 구매 클릭: 공개(PUBLIC) 책이고 링크가 있으면 책 주인 카운트를 올리고 링크를 돌려준다(남의 책방 경로)")
    void recordPublicPurchaseClick_publicWithLink_countsOnOwnerAndReturnsLink() {
        User owner = newUser("pubowner@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);
        book.makePublic(); // 같은 트랜잭션의 영속 엔티티 — 공개로 전환

        String link = bookService.recordPublicPurchaseClick(book.getId());

        assertThat(link).isEqualTo(ALADIN_LINK);
        // 클릭은 viewer가 아니라 "그 책(=책 주인 행)"에 집계된다 — 사용자 결정(2026-06-06)
        assertThat(bookService.myBooks(owner).get(0).getClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("공개 책 구매 클릭: 비공개(PRIVATE) 책이면 null·집계 없음 — 임의 id로 비공개 책을 캐낼 수 없다(프라이버시 게이트)")
    void recordPublicPurchaseClick_privateBook_returnsNullNoCount() {
        User owner = newUser("privowner@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ); // 기본 PRIVATE

        String link = bookService.recordPublicPurchaseClick(book.getId());

        assertThat(link).isNull();
        assertThat(bookService.myBooks(owner).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 책 구매 클릭: 공개여도 구매링크가 없으면 null·집계 없음")
    void recordPublicPurchaseClick_publicNoLink_returnsNullNoCount() {
        User owner = newUser("pubnolink@booktimer.com");
        Book book = bookService.addManual(owner, "수동 공개책", null, BookStatus.READING); // 링크 없음
        book.makePublic();

        String link = bookService.recordPublicPurchaseClick(book.getId());

        assertThat(link).isNull();
        assertThat(bookService.myBooks(owner).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 책 구매 클릭: 없는 책 id면 예외 없이 null(존재 누설 회피)")
    void recordPublicPurchaseClick_missing_returnsNull() {
        String link = bookService.recordPublicPurchaseClick(999_999L);

        assertThat(link).isNull();
    }

    // ── 오픈 리다이렉트 방어 ────────────────────────────────────────────────
    // purchase_link는 POST /api/books가 검증 없이 저장하는 사용자 입력이고, 이 두 메서드의 반환값이
    // 그대로 "redirect:"에 실린다(BookController). 공개 경로가 특히 위험하다 — 공격자가 자기 책을
    // 공개해 두면 우리 도메인 주소 한 줄로 임의 사이트에 착지시킬 수 있다.

    @Test
    @DisplayName("공개 책 구매 클릭: 구매링크가 알라딘이 아니면 null·집계 없음 — 오픈 리다이렉트 차단")
    void recordPublicPurchaseClick_foreignLink_returnsNullNoCount() {
        User owner = newUser("evilpub@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);
        book.updatePurchaseLink("https://evil.example/phish");
        book.makePublic();

        String link = bookService.recordPublicPurchaseClick(book.getId());

        assertThat(link).isNull();
        // 갈 곳이 없으니 집계도 하지 않는다(링크 없는 책과 같은 취급).
        assertThat(bookService.myBooks(owner).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("구매 클릭: 내 책이어도 구매링크가 알라딘이 아니면 null·집계 없음")
    void recordPurchaseClick_foreignLink_returnsNullNoCount() {
        User u = newUser("evilown@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);
        book.updatePurchaseLink("https://evil.example/phish");

        String link = bookService.recordPurchaseClick(u, book.getId());

        assertThat(link).isNull();
        assertThat(bookService.myBooks(u).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 설정: 새 책은 비공개 기본이고, 소유자는 공개/비공개를 바꿀 수 있다")
    void setVisibility_byOwner() {
        User u = newUser("vis@booktimer.com");
        Book book = bookService.addManual(u, "공개 토글 책", null, BookStatus.READING);
        assertThat(book.getVisibility()).isEqualTo(BookVisibility.PRIVATE); // 기본 비공개

        Book published = bookService.setVisibility(u, book.getId(), BookVisibility.PUBLIC);
        assertThat(published.getVisibility()).isEqualTo(BookVisibility.PUBLIC);

        Book hidden = bookService.setVisibility(u, book.getId(), BookVisibility.PRIVATE);
        assertThat(hidden.getVisibility()).isEqualTo(BookVisibility.PRIVATE);
    }

    @Test
    @DisplayName("공개 설정: 남의 책 공개 변경은 거부된다(IDOR 방지)")
    void setVisibility_rejectsNonOwner() {
        User owner = newUser("vo@booktimer.com");
        User attacker = newUser("va@booktimer.com");
        Book book = bookService.addManual(owner, "남의 책", null, BookStatus.READING);

        assertThatThrownBy(() -> bookService.setVisibility(attacker, book.getId(), BookVisibility.PUBLIC))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(bookService.myBooks(owner).get(0).getVisibility()).isEqualTo(BookVisibility.PRIVATE);
    }

    @Test
    @DisplayName("검색은 검색 클라이언트(포트)에 검색기준·페이지와 함께 위임한다")
    void search_delegatesToClient() {
        when(searchClient.search("클린", BookSearchType.TITLE, 2))
                .thenReturn(new BookSearchPage(List.of(cleanCode()), 2, 10, 15));

        BookSearchPage page = bookService.search("클린", BookSearchType.TITLE, 2);

        assertThat(page.results()).hasSize(1);
        assertThat(page.results().get(0).title()).isEqualTo("클린 코드");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.hasNext()).isFalse(); // 15건/10 = 2페이지, 현재 2페이지 = 마지막
    }

    @Test
    @DisplayName("검색 기준(저자)도 그대로 클라이언트에 전달한다")
    void search_passesSearchType() {
        when(searchClient.search("모기", BookSearchType.AUTHOR, 1))
                .thenReturn(new BookSearchPage(List.of(cleanCode()), 1, 10, 1));

        bookService.search("모기", BookSearchType.AUTHOR, 1);

        org.mockito.Mockito.verify(searchClient).search("모기", BookSearchType.AUTHOR, 1);
    }

    @Test
    @DisplayName("제목 검색은 제목에 검색어가 든 결과만 남긴다(알라딘 비엄격 Title 방어)")
    void search_title_keepsOnlyTitleMatches() {
        BookSearchResult titleMatch = new BookSearchResult("황소 엉덩이를 찌른 모기", "이창건", "1", null, "하늘우물", null);
        BookSearchResult authorOnly = new BookSearchResult("철학은 어떻게 인생의 길이 되는가", "모기 겐이치로", "2", null, "다산초당", null);
        when(searchClient.search("모기", BookSearchType.TITLE, 1))
                .thenReturn(new BookSearchPage(List.of(titleMatch, authorOnly), 1, 10, 2));

        BookSearchPage page = bookService.search("모기", BookSearchType.TITLE, 1);

        assertThat(page.results()).extracting(BookSearchResult::title)
                .containsExactly("황소 엉덩이를 찌른 모기"); // 저자만 매칭된 책은 제외
    }

    /**
     * 세트 상품 제외 — 실기기 제보(2026-08-21): 「노벨라33 세트 - 전33권」이 추천 카드에 떠서, 저자
     * 40명이 한 줄을 세로 900px로 부풀려 창을 통째로 먹었다. 화면은 줄 고정으로 따로 막지만
     * <b>세트는 애초에 「이어 읽기」에 안 맞는다</b> — 여기는 다음에 읽을 <b>한 권</b>을 권하는 자리다.
     *
     * <p>검색과 추천이 <b>둘 다 이 메서드를 지나므로</b>(BookApiController · BookRecommendationService)
     * 걸러 내는 곳도 여기 한 곳이다.
     */
    @Test
    @DisplayName("세트 상품은 검색 결과에서 뺀다 — 「이어 읽기」는 한 권을 권하는 자리다")
    void search_dropsBoxSets() {
        BookSearchResult single = new BookSearchResult("전쟁과 평화 3", "톨스토이", "1", null, "민음사", null);
        BookSearchResult boxSet = new BookSearchResult("노벨라33 세트 - 전33권", "여럿", "2", null, "현대문학", null);
        when(searchClient.search("톨스토이", BookSearchType.AUTHOR, 1))
                .thenReturn(new BookSearchPage(List.of(single, boxSet), 1, 10, 2));

        BookSearchPage page = bookService.search("톨스토이", BookSearchType.AUTHOR, 1);

        assertThat(page.results()).extracting(BookSearchResult::title).containsExactly("전쟁과 평화 3");
    }

    /**
     * 의도를 존중한다 — 세트를 <b>일부러</b> 검색한 사람에게서 그 책을 빼앗으면, 자기가 가진 책을
     * 서재에 담을 길이 아예 없어진다. 걸러 내기의 유일한 탈출구라 반드시 남긴다.
     */
    @Test
    @DisplayName("검색어가 세트를 찾고 있으면 거르지 않는다 — 가진 책을 담을 길을 막지 않는다")
    void search_keepsBoxSets_whenQueryWantsThem() {
        BookSearchResult boxSet = new BookSearchResult("노벨라33 세트 - 전33권", "여럿", "2", null, "현대문학", null);
        when(searchClient.search("노벨라33 세트", BookSearchType.TITLE, 1))
                .thenReturn(new BookSearchPage(List.of(boxSet), 1, 10, 1));

        BookSearchPage page = bookService.search("노벨라33 세트", BookSearchType.TITLE, 1);

        assertThat(page.results()).extracting(BookSearchResult::title).containsExactly("노벨라33 세트 - 전33권");
    }

    @Test
    @DisplayName("저자 검색은 저자에 검색어가 든 결과만 남긴다")
    void search_author_keepsOnlyAuthorMatches() {
        BookSearchResult titleMatch = new BookSearchResult("황소 엉덩이를 찌른 모기", "이창건", "1", null, "하늘우물", null);
        BookSearchResult authorMatch = new BookSearchResult("철학은 어떻게 인생의 길이 되는가", "모기 겐이치로", "2", null, "다산초당", null);
        when(searchClient.search("모기", BookSearchType.AUTHOR, 1))
                .thenReturn(new BookSearchPage(List.of(titleMatch, authorMatch), 1, 10, 2));

        BookSearchPage page = bookService.search("모기", BookSearchType.AUTHOR, 1);

        assertThat(page.results()).extracting(BookSearchResult::author)
                .containsExactly("모기 겐이치로"); // 제목만 매칭된 책은 제외
    }

    @Test
    @DisplayName("출판사 검색은 출판사에 검색어가 든 결과만 남긴다")
    void search_publisher_keepsOnlyPublisherMatches() {
        BookSearchResult publisherMatch = new BookSearchResult("자바의 정석", "남궁성", "1", null, "도우출판", null);
        BookSearchResult titleOnly = new BookSearchResult("도우출판 30년사", "아무개", "2", null, "딴곳", null);
        when(searchClient.search("도우출판", BookSearchType.PUBLISHER, 1))
                .thenReturn(new BookSearchPage(List.of(publisherMatch, titleOnly), 1, 10, 2));

        BookSearchPage page = bookService.search("도우출판", BookSearchType.PUBLISHER, 1);

        assertThat(page.results()).extracting(BookSearchResult::publisher)
                .containsExactly("도우출판"); // 제목에만 "도우출판"이 든 책은 제외
    }

    @Test
    @DisplayName("출판사 검색에서 출판사가 null인 결과는 제외된다(미완성 메타가 새지 않음)")
    void search_publisher_excludesNullPublisher() {
        BookSearchResult withPub = new BookSearchResult("책A", "저자A", "1", null, "민음사", null);
        BookSearchResult nullPub = new BookSearchResult("민음사 그 시절", "저자B", "2", null, null, null);
        when(searchClient.search("민음사", BookSearchType.PUBLISHER, 1))
                .thenReturn(new BookSearchPage(List.of(withPub, nullPub), 1, 10, 2));

        BookSearchPage page = bookService.search("민음사", BookSearchType.PUBLISHER, 1);

        assertThat(page.results()).extracting(BookSearchResult::publisher)
                .containsExactly("민음사"); // publisher=null인 둘째 책은 제목에 "민음사"가 있어도 제외
    }

    @Test
    @DisplayName("공백·대소문자 차이는 무시하고 매칭한다(정규화 후 contains)")
    void search_normalizesWhitespaceAndCase() {
        BookSearchResult r = new BookSearchResult("Clean Code 클린 코드", "Robert C. Martin", "1", null, null, null);
        when(searchClient.search("cleancode", BookSearchType.TITLE, 1))
                .thenReturn(new BookSearchPage(List.of(r), 1, 10, 1));

        BookSearchPage page = bookService.search("cleancode", BookSearchType.TITLE, 1);

        assertThat(page.results()).hasSize(1); // "Clean Code 클린 코드" → "cleancode클린코드" 가 "cleancode" 포함
    }

    @Test
    @DisplayName("검색 활성 여부는 클라이언트 설정을 따른다")
    void searchEnabled_reflectsClient() {
        when(searchClient.isEnabled()).thenReturn(true);
        assertThat(bookService.searchEnabled()).isTrue();
    }
}
