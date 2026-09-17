package com.booktimer.web;

import com.booktimer.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 양옆 세로 바의 경로 판정 — 활성 메뉴·모드·내 책방 링크 가드(순수, Spring 없음).
 *
 * <p>설계 claude-docs/plans/2026-09-15-web-side-rails.md §4-① 매핑 표를 그대로 박는다.
 */
class RailNavTest {

    @ParameterizedTest(name = "{0} (loginId={1}) → {2}")
    @CsvSource(nullValues = "NULL", value = {
            "/,              alice, HOME",
            "/dashboard,     alice, HOME",
            "/books,         alice, BOOKS",
            "/books/,        alice, BOOKS",
            "/books/readers, alice, BOOKS",
            "/books/12,      alice, BOOKS",
            "/booksx,        alice, NULL",       // 접두 오탐
            "/u/alice,       alice, SHOP",
            "/u/bob,         alice, NULL",       // 남의 책방
            "/u/alice,       NULL,  NULL",       // 온보딩 전 — 내 책방이 없다
            "/u/alice/books/3/buy, alice, NULL", // 책방 아래 리다이렉트 경로는 활성 아님
            "/personality,   alice, PERSONALITY",
            "/history,       alice, HISTORY",
            "/search,        alice, SEARCH",
            "/study,         alice, PLAN",
            "/study/,        alice, PLAN",
            "/study/recall,  alice, RECALL",
            "/study/recall/, alice, RECALL",
            "/study/books,   alice, SBOOKS",
            "/study/history, alice, SHISTORY",
            "/me/blocks,     alice, NULL",
            "/me/followers,  alice, NULL",
            "/settings,      alice, NULL",
            "/feedback,      alice, NULL",
            "/sessions/manual, alice, NULL",
    })
    @DisplayName("경로 → 활성 키")
    void activeKey(String path, String loginId, RailNav.Key expected) {
        assertThat(RailNav.activeKey(path, loginId)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} (loginId={1}) → remember {2}")
    @CsvSource(nullValues = "NULL", value = {
            "/,               alice, NULL",       // 홈 — Vue 토글이 주인
            "/dashboard,      alice, NULL",
            "/books,          alice, NULL",       // 독서 쪽은 안 건드린다(사용자 결정 2026-09-16)
            "/books/12,       alice, NULL",       // 접두 = 내 책장 섹션
            "/u/alice,        alice, NULL",       // 내 책방
            "/u/bob,          alice, NULL",       // 남의 책방 — 활성 없음 → 안 건드린다
            "/u/alice,        NULL,  NULL",       // 온보딩 전
            "/personality,    alice, NULL",
            "/history,        alice, NULL",
            "/search,         alice, NULL",
            "/study,          alice, study",
            "/study/recall,   alice, study",
            "/study/books,    alice, study",
            "/study/history,  alice, study",
            "/settings,       alice, NULL",       // 중립 페이지 — 공부 모드가 독서로 튀면 안 된다
            "/feedback,       alice, NULL",
            "/me/blocks,      alice, NULL",
            "/sessions/manual, alice, NULL",
            "/studyx,         alice, NULL",       // 접두 오탐
    })
    @DisplayName("경로 → 기억할 모드(공부 바 페이지만 — 독서 쪽은 안 건드린다)")
    void rememberMode(String path, String loginId, String expected) {
        assertThat(RailNav.rememberMode(path, loginId)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/study,         study",
            "/study/,        study",
            "/study/books,   study",
            "/study/recall,  study",
            "/study/history, study",
            "/studyx,        reading",
            "/,              reading",
            "/books,         reading",
            "/history,       reading",
    })
    @DisplayName("경로 → 모드(/study 정확 또는 /study/ 접두만 공부)")
    void mode(String path, String expected) {
        assertThat(RailNav.mode(path)).isEqualTo(expected);
    }

    @Test
    @DisplayName("내 책방 href — 일반 사용자는 /u/{loginId}")
    void shopHref_user() {
        assertThat(RailNav.shopHref("alice", Role.USER)).isEqualTo("/u/alice");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("내 책방 href — loginId가 비었으면 null(/u/ · /u/null 누수 방지)")
    void shopHref_blankLoginId(String loginId) {
        assertThat(RailNav.shopHref(loginId, Role.USER)).isNull();
    }

    @Test
    @DisplayName("내 책방 href — ADMIN은 책방이 404라 null")
    void shopHref_admin() {
        assertThat(RailNav.shopHref("root", Role.ADMIN)).isNull();
    }
}
