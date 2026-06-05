package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ISBN 정규화 단위 테스트 — 책 동일성 키(isbn13)를 적재 시점에 한 표기로 모은다.
 *
 * <p>왜 필요한가: isbn13은 인기 카운트의 <b>group by 키</b>다(BookRepository.followScopePopularity).
 * 표기가 갈리면 같은 책이 쪼개지고, 빈 값을 ""로 저장하면 ISBN 없는 서로 다른 책들이 하나로 뭉친다.
 * 그래서 ① 빈 값/구분자뿐인 값은 NULL로, ② 하이픈·공백은 제거해 13자리 숫자열로 모은다.
 */
class IsbnTest {

    @Test
    @DisplayName("null은 그대로 null")
    void nullStaysNull() {
        assertThat(Isbn.normalize(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 null로 모은다 — ISBN 없는 책끼리 \"\" 하나로 뭉치지 않게")
    void blankBecomesNull() {
        assertThat(Isbn.normalize("")).isNull();
        assertThat(Isbn.normalize("   ")).isNull();
        assertThat(Isbn.normalize("\t \n")).isNull();
    }

    @Test
    @DisplayName("구분자(하이픈/공백)뿐이면 알맹이가 없으니 null")
    void separatorsOnlyBecomesNull() {
        assertThat(Isbn.normalize("-")).isNull();
        assertThat(Isbn.normalize(" - - ")).isNull();
    }

    @Test
    @DisplayName("하이픈을 제거해 숫자열로 모은다")
    void stripsHyphens() {
        assertThat(Isbn.normalize("978-89-954321-0-7")).isEqualTo("9788995432107");
    }

    @Test
    @DisplayName("내부·양끝 공백을 제거한다")
    void stripsWhitespace() {
        assertThat(Isbn.normalize("  9788995432107  ")).isEqualTo("9788995432107");
        assertThat(Isbn.normalize("978 89 954321 0 7")).isEqualTo("9788995432107");
    }

    @Test
    @DisplayName("이미 정규형(13자리 숫자)이면 그대로 둔다")
    void alreadyNormalizedUnchanged() {
        assertThat(Isbn.normalize("9788995432107")).isEqualTo("9788995432107");
    }
}
