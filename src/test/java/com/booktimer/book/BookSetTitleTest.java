package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세트 상품 판정 — 「이어 읽기」는 다음에 읽을 <b>한 권</b>을 권하는 자리라 구매 묶음을 뺀다.
 *
 * <p>판정은 제목 문자열 휴리스틱이라 <b>오탐이 진짜 비용</b>이다: 전집처럼 그 자체가 한 권인 책을
 * 걸러 버리면 가진 책을 담을 길이 없어진다. 그래서 아래 오탐 방어가 이 클래스의 핵심 테스트다.
 */
class BookSetTitleTest {

    @Test
    @DisplayName("「전N권」이 붙으면 세트다 — 한 권짜리 책이 자기 제목에 이렇게 쓰는 일은 없다")
    void volumeCount_isSet() {
        assertThat(BookSetTitle.isSet("노벨라33 세트 - 전33권")).isTrue();
        assertThat(BookSetTitle.isSet("해리 포터 전7권")).isTrue();
        assertThat(BookSetTitle.isSet("토지 전 21 권")).isTrue(); // 공백이 섞여도
    }

    @Test
    @DisplayName("「세트」가 붙으면 세트다")
    void setWord_isSet() {
        assertThat(BookSetTitle.isSet("셜록 홈즈 시리즈 세트")).isTrue();
        assertThat(BookSetTitle.isSet("무민 박스세트")).isTrue();
    }

    /**
     * 오탐 방어 — 여기가 이 판정의 진짜 위험이다. 걸러진 책은 검색에서 사라져 <b>가진 책을 담을 수
     * 없게</b> 되므로, 애매하면 통과시키는 쪽으로 기운다.
     */
    @Test
    @DisplayName("「전집」·「전권」은 세트가 아니다 — 그 자체로 한 권인 책이 있다")
    void collection_isNotSet() {
        assertThat(BookSetTitle.isSet("셜록 홈즈 전집")).isFalse();
        assertThat(BookSetTitle.isSet("한국 단편소설 전권 해설")).isFalse();
    }

    @Test
    @DisplayName("평범한 제목은 세트가 아니다")
    void plainTitle_isNotSet() {
        assertThat(BookSetTitle.isSet("전쟁과 평화 3")).isFalse();
        assertThat(BookSetTitle.isSet("안나 카레니나 1")).isFalse();
        assertThat(BookSetTitle.isSet("1권으로 읽는 세계사")).isFalse(); // 「N권」만으로는 부족하다
    }

    @Test
    @DisplayName("null·빈 제목은 세트가 아니다 — 판정 불가를 「세트」로 읽지 않는다")
    void nullTitle_isNotSet() {
        assertThat(BookSetTitle.isSet(null)).isFalse();
        assertThat(BookSetTitle.isSet("  ")).isFalse();
    }

    /**
     * 검색어가 세트를 찾고 있으면 거르지 않는다 — 「노벨라33 세트」를 <b>일부러</b> 검색한 사람에게서
     * 그 책을 빼앗으면, 자기가 가진 책을 서재에 담을 길이 없다.
     */
    @Test
    @DisplayName("검색어 자체가 세트를 찾으면 거르지 않는다 — 의도를 존중한다")
    void queryWantsSet() {
        assertThat(BookSetTitle.wanted("노벨라33 세트")).isTrue();
        assertThat(BookSetTitle.wanted("해리포터 전7권")).isTrue();
        assertThat(BookSetTitle.wanted("톨스토이")).isFalse();
        assertThat(BookSetTitle.wanted(null)).isFalse();
    }
}
