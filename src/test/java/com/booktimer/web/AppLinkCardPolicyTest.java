package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 웹 「앱 연결」 카드 허용 목록(v1 REQ-73) — 스토어에 없는 앱의 카드를 출시 전까지 정한 사람에게만 보인다. */
class AppLinkCardPolicyTest {

    @Test
    @DisplayName("REQ-73 · 목록이 「17」이면 17만 보이고 18은 안 보인다")
    void listedIdOnly() {
        assertThat(AppLinkCardPolicy.visible(17, false, "17")).isTrue();
        assertThat(AppLinkCardPolicy.visible(18, false, "17")).isFalse();
        assertThat(AppLinkCardPolicy.visible(18, false, " 17 , 18 ")).isTrue(); // 쉼표 목록 · 공백 허용
        assertThat(AppLinkCardPolicy.visible(171, false, "17")).isFalse(); // 접두 일치가 아니다
    }

    @Test
    @DisplayName("REQ-73 · 「*」이면 모두 보인다")
    void starMeansEveryone() {
        assertThat(AppLinkCardPolicy.visible(18, false, "*")).isTrue();
        assertThat(AppLinkCardPolicy.visible(1, false, "17,*")).isTrue();
    }

    @Test
    @DisplayName("REQ-73 · 숫자가 아닌 값(「none」)이면 아무도 안 보인다")
    void nonNumericMeansNobody() {
        assertThat(AppLinkCardPolicy.visible(17, false, "none")).isFalse();
        assertThat(AppLinkCardPolicy.visible(17, false, "not-configured")).isFalse();
        assertThat(AppLinkCardPolicy.visible(17, false, "")).isFalse();
        assertThat(AppLinkCardPolicy.visible(17, false, null)).isFalse();
        assertThat(AppLinkCardPolicy.visible(17, false, "abc,17")).isTrue(); // 숫자 아닌 칸만 무시한다
    }

    @Test
    @DisplayName("REQ-73 · Apple이 연결된 사용자는 목록 밖이어도 보인다")
    void linkedAlwaysSees() {
        assertThat(AppLinkCardPolicy.visible(18, true, "none")).isTrue();
        assertThat(AppLinkCardPolicy.visible(18, true, "17")).isTrue();
    }
}
