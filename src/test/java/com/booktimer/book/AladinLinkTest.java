package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알라딘 제휴 구매링크 신뢰 판정 단위 테스트.
 *
 * <p>왜 필요한가: {@code book.purchase_link}는 <b>사용자 입력</b>이다(POST /api/books의 요청 본문이
 * 검증 없이 그대로 저장된다). 그 값이 {@code "redirect:" + link}로 나가므로(BookController), 판정이 없으면
 * 공개 책 하나로 <b>오픈 리다이렉트</b>가 만들어진다 — 공격자가 자기 책을 공개해 두고
 * {@code booktimer.app/u/<공격자>/books/<id>/buy} 를 뿌리면 우리 도메인을 거쳐 임의 사이트로 튄다.
 *
 * <p>그래서 판정은 <b>host 완전일치</b>여야 한다. {@code startsWith("https://www.aladin.co.kr")} 같은
 * 접두 비교는 {@code https://www.aladin.co.kr.evil.example} 에 그대로 뚫리고, "문자열에 aladin이 있나"류의
 * 포함 비교는 쿼리스트링에 심은 미끼에 뚫린다. 아래 테스트는 그 우회들을 하나씩 못 박는다.
 */
class AladinLinkTest {

    @Test
    @DisplayName("진짜 알라딘 상품 링크는 통과한다 — API가 실제로 돌려주는 형식")
    void realAladinProductLinkTrusted() {
        assertThat(AladinLink.isTrusted(
                "https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=12345&partner=openAPI&start=api&ttbkey=k"))
                .isTrue();
    }

    @Test
    @DisplayName("알라딘 API의 link는 http로 오기도 한다 — 평문도 통과(제휴 추적을 깨지 않게)")
    void plainHttpAladinTrusted() {
        assertThat(AladinLink.isTrusted("http://www.aladin.co.kr/shop/wproduct.aspx?ItemId=1")).isTrue();
    }

    @Test
    @DisplayName("www 없는 정규 도메인도 통과한다")
    void apexDomainTrusted() {
        assertThat(AladinLink.isTrusted("https://aladin.co.kr/shop/wproduct.aspx?ItemId=1")).isTrue();
    }

    @Test
    @DisplayName("접두 비교였다면 뚫렸을 서브도메인 사칭을 막는다 — aladin.co.kr 뒤에 붙인 진짜 호스트")
    void suffixedLookalikeHostRejected() {
        assertThat(AladinLink.isTrusted("https://www.aladin.co.kr.evil.example/shop/wproduct.aspx")).isFalse();
    }

    @Test
    @DisplayName("userinfo 사칭을 막는다 — @ 앞은 아이디일 뿐 진짜 목적지는 뒤쪽 호스트다")
    void userInfoTrickRejected() {
        assertThat(AladinLink.isTrusted("https://www.aladin.co.kr@evil.example/")).isFalse();
    }

    @Test
    @DisplayName("포함 비교였다면 뚫렸을 쿼리스트링 미끼를 막는다")
    void aladinInQueryStringRejected() {
        assertThat(AladinLink.isTrusted("https://evil.example/?r=https://www.aladin.co.kr/shop")).isFalse();
    }

    @Test
    @DisplayName("프로토콜 상대 URL을 막는다 — //로 시작하면 브라우저는 현재 스킴으로 외부에 붙는다")
    void protocolRelativeRejected() {
        assertThat(AladinLink.isTrusted("//evil.example/shop")).isFalse();
    }

    @Test
    @DisplayName("http·https가 아닌 스킴을 막는다")
    void nonHttpSchemeRejected() {
        assertThat(AladinLink.isTrusted("javascript:alert(1)")).isFalse();
        assertThat(AladinLink.isTrusted("data:text/html,<script>alert(1)</script>")).isFalse();
        assertThat(AladinLink.isTrusted("ftp://www.aladin.co.kr/x")).isFalse();
    }

    @Test
    @DisplayName("대소문자만 다른 호스트도 같은 호스트다 — 판정을 대문자로 우회할 수 없게")
    void hostComparisonIsCaseInsensitive() {
        assertThat(AladinLink.isTrusted("https://WWW.ALADIN.CO.KR/shop/wproduct.aspx")).isTrue();
    }

    @Test
    @DisplayName("파싱조차 안 되는 값은 거부한다 — 예외로 새어나가지 않는다")
    void malformedRejected() {
        assertThat(AladinLink.isTrusted("h ttp://www.aladin.co.kr")).isFalse();
        assertThat(AladinLink.isTrusted("://")).isFalse();
    }

    @Test
    @DisplayName("null·빈 값은 거부한다(링크 없음과 같은 취급)")
    void nullOrBlankRejected() {
        assertThat(AladinLink.isTrusted(null)).isFalse();
        assertThat(AladinLink.isTrusted("")).isFalse();
        assertThat(AladinLink.isTrusted("   ")).isFalse();
    }
}
