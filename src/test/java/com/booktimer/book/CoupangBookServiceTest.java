package com.booktimer.book;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

/**
 * 쿠팡 구매 클릭 유스케이스 테스트 — 알라딘 클릭({@link BookServiceTest})과 대칭.
 *
 * <p>쿠팡 링크 생성은 {@link CoupangLinkBuilder}에 위임하므로 빌더를 mock해 enabled/disabled 두 상태를
 * 제어한다. 알라딘과 달리 쿠팡은 링크가 늘 생성돼(제목 폴백) null 사유가 "비활성(추적코드 미설정)"뿐이라,
 * disabled = 빌더가 null 반환으로 모델링한다.
 *
 * <p>{@link CoupangDeeplinkClient}도 mock한다 — 미스텁 호출은 Mockito 기본값(Optional 반환 메서드는
 * {@code Optional.empty()})이라, 별도 스텁 없는 기존 테스트는 "딥링크 API 미설정/실패 → raw URL 폴백"
 * 경로를 그대로 검증한다(회귀). 딥링크 API가 성공하는 경로는 별도 테스트로 명시적으로 스텁한다.
 */
@SpringBootTest
@Transactional
class CoupangBookServiceTest {

    @Autowired
    private BookService bookService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private BookSearchClient searchClient;
    @MockitoBean
    private CoupangLinkBuilder coupangLinkBuilder;
    @MockitoBean
    private CoupangDeeplinkClient deeplinkClient;

    private static final String COUPANG_LINK = "https://www.coupang.com/np/search?q=x&lptag=AF1234567";

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    private static BookSearchResult cleanCode() {
        return new BookSearchResult("클린 코드", "로버트 마틴", "9788966260959",
                "http://cover/clean.jpg", "인사이트", "http://aladin/buy?ttbkey=x");
    }

    @Test
    @DisplayName("쿠팡 클릭: 내 책이고 활성이면 쿠팡 카운트를 올리고 런타임 생성 링크를 돌려준다")
    void recordCoupangClick_ownedAndEnabled_countsAndReturnsLink() {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        User u = newUser("cbuy@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordCoupangClick(u, book.getId());

        assertThat(link).isEqualTo(COUPANG_LINK);
        assertThat(bookService.myBooks(u).get(0).getCoupangClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("쿠팡 클릭: 딥링크 API가 추적링크를 돌려주면 raw 대신 그 추적링크로 리다이렉트한다")
    void recordCoupangClick_deeplinkSucceeds_returnsTrackingLinkInsteadOfRaw() {
        String trackingLink = "https://link.coupang.com/a/abc123";
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        when(deeplinkClient.toTrackingLink(eq(COUPANG_LINK))).thenReturn(Optional.of(trackingLink));
        User u = newUser("cdeeplink@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordCoupangClick(u, book.getId());

        assertThat(link).isEqualTo(trackingLink);
        assertThat(bookService.myBooks(u).get(0).getCoupangClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("쿠팡 클릭: 딥링크 API가 실패(빈 값)하면 raw 검색 URL로 폴백하되 집계는 유지한다")
    void recordCoupangClick_deeplinkFails_fallsBackToRawLinkButStillCounts() {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        when(deeplinkClient.toTrackingLink(eq(COUPANG_LINK))).thenReturn(Optional.empty());
        User u = newUser("cdeeplinkfail@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordCoupangClick(u, book.getId());

        assertThat(link).isEqualTo(COUPANG_LINK);
        assertThat(bookService.myBooks(u).get(0).getCoupangClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("쿠팡 클릭: 남의 책이면 거부된다(IDOR 방지) — 집계 없음")
    void recordCoupangClick_nonOwner_rejected() {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        User owner = newUser("co3@booktimer.com");
        User attacker = newUser("ca3@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);

        assertThatThrownBy(() -> bookService.recordCoupangClick(attacker, book.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bookService.myBooks(owner).get(0).getCoupangClickCount()).isZero();
    }

    @Test
    @DisplayName("쿠팡 클릭: 비활성(추적코드 미설정)이면 null·집계 없음")
    void recordCoupangClick_disabled_returnsNullNoCount() {
        // 빌더 stub 없음 → buildSearchLink가 null(비활성)을 반환
        User u = newUser("cdisabled@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordCoupangClick(u, book.getId());

        assertThat(link).isNull();
        assertThat(bookService.myBooks(u).get(0).getCoupangClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 책 쿠팡 클릭: 공개·활성이면 책 주인 카운트를 올리고 링크를 돌려준다(남의 책방 경로)")
    void recordPublicCoupangClick_publicAndEnabled_countsOnOwnerAndReturnsLink() {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        User owner = newUser("cpubowner@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);
        book.makePublic();

        String link = bookService.recordPublicCoupangClick(book.getId());

        assertThat(link).isEqualTo(COUPANG_LINK);
        assertThat(bookService.myBooks(owner).get(0).getCoupangClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("공개 책 쿠팡 클릭: 비공개 책이면 null·집계 없음(임의 id로 비공개 책 캐내기 차단)")
    void recordPublicCoupangClick_privateBook_returnsNullNoCount() {
        User owner = newUser("cprivowner@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ); // 기본 PRIVATE

        String link = bookService.recordPublicCoupangClick(book.getId());

        assertThat(link).isNull();
        assertThat(bookService.myBooks(owner).get(0).getCoupangClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 책 쿠팡 클릭: 없는 책 id면 예외 없이 null(존재 누설 회피)")
    void recordPublicCoupangClick_missing_returnsNull() {
        assertThat(bookService.recordPublicCoupangClick(999_999L)).isNull();
    }

    @Test
    @DisplayName("쿠팡 활성 여부는 빌더 설정을 따른다")
    void coupangEnabled_reflectsBuilder() {
        when(coupangLinkBuilder.isEnabled()).thenReturn(true);
        assertThat(bookService.coupangEnabled()).isTrue();
    }
}
