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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yes24 구매 클릭 유스케이스 테스트 — 쿠팡 클릭({@link CoupangBookServiceTest})과 대칭.
 *
 * <p>Yes24 링크 생성은 {@link Yes24LinkBuilder}에 위임하므로 빌더를 mock해 enabled/disabled 두 상태를
 * 제어한다. 쿠팡과 마찬가지로 링크가 늘 생성돼(제목 폴백) null 사유가 "비활성(추적코드 미설정)"뿐이라,
 * disabled = 빌더가 null 반환으로 모델링한다.
 *
 * <p>모바일 UA 분기(T-128)는 컨트롤러가 판별한 {@code mobileDevice}를 그대로 빌더에 전달하는지만 본다 —
 * UA 판별 자체(경계값)는 {@link Yes24LinkBuilderTest#isMobileUserAgent_detectsKnownMobileDevices()}가 담당.
 */
@SpringBootTest
@Transactional
class Yes24BookServiceTest {

    @Autowired
    private BookService bookService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private BookSearchClient searchClient;
    @MockitoBean
    private Yes24LinkBuilder yes24LinkBuilder;

    private static final String YES24_LINK = "https://www.yes24.com/product/search?query=x&pid=LP1234567";
    private static final String YES24_MOBILE_LINK = "https://m.yes24.com/search?query=x";

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    private static BookSearchResult cleanCode() {
        return new BookSearchResult("클린 코드", "로버트 마틴", "9788966260959",
                "http://cover/clean.jpg", "인사이트", "http://aladin/buy?ttbkey=x");
    }

    @Test
    @DisplayName("Yes24 클릭: 내 책이고 활성이면 Yes24 카운트를 올리고 런타임 생성 링크를 돌려준다(mobile=false)")
    void recordYes24Click_ownedAndEnabled_countsAndReturnsLink() {
        when(yes24LinkBuilder.buildSearchLink(any(), eq(false))).thenReturn(YES24_LINK);
        User u = newUser("ybuy@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordYes24Click(u, book.getId(), false);

        assertThat(link).isEqualTo(YES24_LINK);
        assertThat(bookService.myBooks(u).get(0).getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Yes24 클릭: mobile=true면 빌더에 mobile=true를 전달하고 모바일 링크를 돌려준다(T-128)")
    void recordYes24Click_mobile_passesMobileFlagAndReturnsMobileLink() {
        when(yes24LinkBuilder.buildSearchLink(any(), eq(true))).thenReturn(YES24_MOBILE_LINK);
        User u = newUser("ybuymobile@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordYes24Click(u, book.getId(), true);

        assertThat(link).isEqualTo(YES24_MOBILE_LINK);
        verify(yes24LinkBuilder).buildSearchLink(any(), eq(true));
        assertThat(bookService.myBooks(u).get(0).getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Yes24 클릭: 남의 책이면 거부된다(IDOR 방지) — 집계 없음")
    void recordYes24Click_nonOwner_rejected() {
        when(yes24LinkBuilder.buildSearchLink(any(), eq(false))).thenReturn(YES24_LINK);
        User owner = newUser("yo3@booktimer.com");
        User attacker = newUser("ya3@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);

        assertThatThrownBy(() -> bookService.recordYes24Click(attacker, book.getId(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bookService.myBooks(owner).get(0).getYes24ClickCount()).isZero();
    }

    @Test
    @DisplayName("Yes24 클릭: 비활성(추적코드 미설정)이면 null·집계 없음")
    void recordYes24Click_disabled_returnsNullNoCount() {
        // 빌더 stub 없음 → buildSearchLink가 null(비활성)을 반환
        User u = newUser("ydisabled@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordYes24Click(u, book.getId(), false);

        assertThat(link).isNull();
        assertThat(bookService.myBooks(u).get(0).getYes24ClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 책 Yes24 클릭: 공개·활성이면 책 주인 카운트를 올리고 링크를 돌려준다(남의 책방 경로, mobile=false)")
    void recordPublicYes24Click_publicAndEnabled_countsOnOwnerAndReturnsLink() {
        when(yes24LinkBuilder.buildSearchLink(any(), eq(false))).thenReturn(YES24_LINK);
        User owner = newUser("ypubowner@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);
        book.makePublic();

        String link = bookService.recordPublicYes24Click(book.getId(), false);

        assertThat(link).isEqualTo(YES24_LINK);
        assertThat(bookService.myBooks(owner).get(0).getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("공개 책 Yes24 클릭: mobile=true면 빌더에 mobile=true를 전달하고 모바일 링크를 돌려준다(T-128)")
    void recordPublicYes24Click_mobile_passesMobileFlagAndReturnsMobileLink() {
        when(yes24LinkBuilder.buildSearchLink(any(), eq(true))).thenReturn(YES24_MOBILE_LINK);
        User owner = newUser("ypubmobile@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);
        book.makePublic();

        String link = bookService.recordPublicYes24Click(book.getId(), true);

        assertThat(link).isEqualTo(YES24_MOBILE_LINK);
        verify(yes24LinkBuilder).buildSearchLink(any(), eq(true));
        assertThat(bookService.myBooks(owner).get(0).getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("공개 책 Yes24 클릭: 비공개 책이면 null·집계 없음(임의 id로 비공개 책 캐내기 차단)")
    void recordPublicYes24Click_privateBook_returnsNullNoCount() {
        User owner = newUser("yprivowner@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ); // 기본 PRIVATE

        String link = bookService.recordPublicYes24Click(book.getId(), false);

        assertThat(link).isNull();
        assertThat(bookService.myBooks(owner).get(0).getYes24ClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 책 Yes24 클릭: 없는 책 id면 예외 없이 null(존재 누설 회피)")
    void recordPublicYes24Click_missing_returnsNull() {
        assertThat(bookService.recordPublicYes24Click(999_999L, false)).isNull();
    }

    @Test
    @DisplayName("Yes24 활성 여부는 빌더 설정을 따른다")
    void yes24Enabled_reflectsBuilder() {
        when(yes24LinkBuilder.isEnabled()).thenReturn(true);
        assertThat(bookService.yes24Enabled()).isTrue();
    }
}
