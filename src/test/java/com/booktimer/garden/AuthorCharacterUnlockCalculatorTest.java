package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작가 캐릭터 해금 순수 계산 검증 (DB·Spring 무관). 정규화·contains 매칭 경계를 전수로 본다.
 *
 * <p>두 단계:
 * <ol>
 *   <li>{@code normalize} — 알라딘 원문({@code "한강 (지은이)"} 등)에서 역할군·토큰·공백을 제거해
 *       순수 작가명 문자열로 만든다. null/빈/역할만 → "". 경계 전수.</li>
 *   <li>{@code resolve} — 정규화된 보유 작가 집합으로 카탈로그 각 캐릭터의 보유 판정(contains).
 *       빈 matchName 가드, 부분문자열 충돌 명세 테스트 포함.</li>
 * </ol>
 */
class AuthorCharacterUnlockCalculatorTest {

    // 테스트 카탈로그 — prod 시드(V45)와 디커플, 충돌 없는 명칭으로.
    private static final AuthorCharacter HAN_GANG =
            AuthorCharacter.of("han_gang", "한강", "소설가 한강", "🪶", 1, null);
    private static final AuthorCharacter KIM_YOUNG_HA =
            AuthorCharacter.of("kim_young_ha", "김영하", "여행자 김영하", "✈️", 2, null);
    // 빈 matchName — "빈 문자열은 모든 문자열에 contains" 사고를 차단하는 가드 검증용
    private static final AuthorCharacter EMPTY_MATCH =
            AuthorCharacter.of("empty_guard", "", "가드 검증용", "❓", 3, null);
    private static final List<AuthorCharacter> CATALOG = List.of(HAN_GANG, KIM_YOUNG_HA, EMPTY_MATCH);

    // ── normalize() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("괄호 역할군 '(지은이)' 제거 → 작가명만 남음")
    void normalize_removesParenRole() {
        assertThat(AuthorCharacterUnlockCalculator.normalize("한강 (지은이)")).isEqualTo("한강");
    }

    @Test
    @DisplayName("역자 동반 원문 — '(지은이)·(옮긴이)' 모두 제거, 공백 흡수 후 콤마 구분 결과")
    void normalize_removesTranslatorParenRole() {
        // 괄호 역할군 제거 후 공백 제거 → "베르나르베르베르,전미연"
        assertThat(AuthorCharacterUnlockCalculator.normalize(
                "베르나르 베르베르 (지은이), 전미연 (옮긴이)"))
                .isEqualTo("베르나르베르베르,전미연");
    }

    @Test
    @DisplayName("괄호 없는 역할 토큰 '지음' 제거 → 작가명만 남음")
    void normalize_removesNakedRoleToken() {
        assertThat(AuthorCharacterUnlockCalculator.normalize("한강 지음")).isEqualTo("한강");
    }

    @Test
    @DisplayName("중간·앞뒤 공백 모두 제거로 흡수")
    void normalize_removesAllSpaces() {
        assertThat(AuthorCharacterUnlockCalculator.normalize(" 김 영하 ")).isEqualTo("김영하");
    }

    @Test
    @DisplayName("null 입력 → 빈 문자열 (N-055 미완성 메타 누수 가드)")
    void normalize_nullToEmpty() {
        assertThat(AuthorCharacterUnlockCalculator.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열 입력 → 빈 문자열")
    void normalize_emptyToEmpty() {
        assertThat(AuthorCharacterUnlockCalculator.normalize("")).isEmpty();
    }

    @Test
    @DisplayName("공백만 있는 입력 → 빈 문자열")
    void normalize_blankToEmpty() {
        assertThat(AuthorCharacterUnlockCalculator.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("역할군만 있는 입력 '(지은이)' → 빈 문자열")
    void normalize_onlyRoleToEmpty() {
        assertThat(AuthorCharacterUnlockCalculator.normalize("(지은이)")).isEmpty();
    }

    // ── normalizedAuthors() ──────────────────────────────────────────────────

    @Test
    @DisplayName("완독 작가 목록을 정규화 집합으로 변환 — 정규화 결과 빈 문자열은 제외 (N-055)")
    void normalizedAuthors_excludesEmptyAfterNormalize() {
        List<String> raw = Arrays.asList("한강 (지은이)", null, "", "   ", "(지은이)");
        Set<String> result = AuthorCharacterUnlockCalculator.normalizedAuthors(raw);
        assertThat(result).containsExactly("한강");
    }

    @Test
    @DisplayName("같은 이름 다른 표기 — 정규화 후 동일하면 하나로 흡수")
    void normalizedAuthors_deduplicates() {
        List<String> raw = List.of("한강 (지은이)", "한강 지음", "한강");
        Set<String> result = AuthorCharacterUnlockCalculator.normalizedAuthors(raw);
        assertThat(result).hasSize(1).contains("한강");
    }

    // ── resolve() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정확한 작가명 완독 → 해당 캐릭터 owned")
    void resolve_exactName_owned() {
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(List.of("한강"));
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(CATALOG, authors);
        assertThat(state(states, "han_gang").owned()).isTrue();
        assertThat(state(states, "kim_young_ha").owned()).isFalse();
    }

    @Test
    @DisplayName("'(지은이)' 접미 완독 → 정규화 후 contains로 owned")
    void resolve_parenthesizedRole_owned() {
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(List.of("한강 (지은이)"));
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(CATALOG, authors);
        assertThat(state(states, "han_gang").owned()).isTrue();
    }

    @Test
    @DisplayName("역자 동반 완독 — lead 작가가 정규화 결과에 포함돼 contains로 owned")
    void resolve_translatorSuffix_owned() {
        // "베르나르베르베르,전미연".contains("베르나르베르베르") = true
        AuthorCharacter werber = AuthorCharacter.of("werber", "베르나르 베르베르", "개미의 베르베르", "🐜", 4, null);
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(
                List.of("베르나르 베르베르 (지은이), 전미연 (옮긴이)"));
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(List.of(werber), authors);
        assertThat(states.get(0).owned()).isTrue();
    }

    @Test
    @DisplayName("무관 작가만 완독 → 카탈로그 캐릭터 전부 미owned")
    void resolve_unrelatedAuthor_notOwned() {
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(List.of("조정래 (지은이)"));
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(CATALOG, authors);
        assertThat(states).noneMatch(AuthorCharacterState::owned);
    }

    @Test
    @DisplayName("빈/null 작가 책만 완독 → 아무 캐릭터도 미owned (N-055 누수 가드)")
    void resolve_nullOrEmptyAuthorsOnly_noOwned() {
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(
                Arrays.asList(null, "", "   ", "(지은이)"));
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(CATALOG, authors);
        assertThat(states).noneMatch(AuthorCharacterState::owned);
    }

    @Test
    @DisplayName("빈 matchName 캐릭터 — 어떤 책으로도 절대 owned 안 됨 (빈 contains=항상참 사고 차단)")
    void resolve_emptyMatchName_neverOwned() {
        // 보유 작가 집합에 값이 있어도, matchName이 빈 캐릭터는 절대 해금되지 않아야 한다.
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(
                List.of("한강", "김영하", "베르나르 베르베르"));
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(CATALOG, authors);
        assertThat(state(states, "empty_guard").owned()).isFalse();
    }

    @Test
    @DisplayName("완독 0 → 카탈로그 전체가 잠금칸 (도감 공란)")
    void resolve_zeroAuthors_allLocked() {
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(List.of());
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(CATALOG, authors);
        assertThat(states).noneMatch(AuthorCharacterState::owned);
        assertThat(states).extracting(s -> s.character().getCode())
                .containsExactly("han_gang", "kim_young_ha", "empty_guard");
    }

    @Test
    @DisplayName("부분문자열 충돌 데모 — matchName '김영'이 '김영하'를 잡음 (큐레이션이 짧은 이름을 쓰면 안 되는 이유)")
    void resolve_substringCollision_demo() {
        // 이 테스트는 contains 매칭의 한계를 명세로 못 박는다.
        // "김영하".contains("김영") = true → 큐레이션에서 "김영" 단독 matchName은 사용하지 말 것.
        AuthorCharacter kimYoung = AuthorCharacter.of("kim_young", "김영", "충돌데모", "⚠️", 5, null);
        Set<String> authors = AuthorCharacterUnlockCalculator.normalizedAuthors(List.of("김영하 (지은이)"));
        List<AuthorCharacterState> states = AuthorCharacterUnlockCalculator.resolve(List.of(kimYoung), authors);
        assertThat(states.get(0).owned()).isTrue(); // 의도된 충돌 — 큐레이션 가이드 명세
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private static AuthorCharacterState state(List<AuthorCharacterState> states, String code) {
        return states.stream()
                .filter(s -> s.character().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
