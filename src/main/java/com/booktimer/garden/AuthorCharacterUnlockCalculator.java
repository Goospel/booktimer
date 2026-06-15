package com.booktimer.garden;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 작가 캐릭터 해금 순수 계산(DB·Spring·시간 무관).
 *
 * <p>세 단계:
 * <ol>
 *   <li>{@link #normalize} — 알라딘 원문({@code "한강 (지은이)"} 등)에서 역할군 괄호·역할 토큰·
 *       공백을 제거해 비교 가능한 순수 문자열로 만든다. null/빈/역할만 → {@code ""}.</li>
 *   <li>{@link #normalizedAuthors} — 완독책 작가 목록 → 정규화 집합(빈 문자열 제외 — N-055).</li>
 *   <li>{@link #resolve} — 정규화된 보유 작가 집합으로 카탈로그 각 캐릭터의 보유 판정.
 *       매칭: {@code normalizedBookAuthor.contains(normalizedMatchName)}.
 *       빈 {@code matchName}은 절대 매칭 안 됨(빈 contains=항상참 사고 차단).</li>
 * </ol>
 *
 * <p>부분문자열 충돌(예: matchName "김영" ⊂ "김영하")은 카탈로그 큐레이션으로 막는다 —
 * 짧거나 모호한 matchName을 시드에 넣지 않는 것이 책임이다.
 */
public final class AuthorCharacterUnlockCalculator {

    // 괄호 안 역할 어노테이션 제거용 패턴 ("(지은이)", "(옮긴이)", "(그림)" 등 모든 괄호 내용)
    private static final java.util.regex.Pattern PAREN_PATTERN =
            java.util.regex.Pattern.compile("\\([^)]*\\)");

    // 괄호 밖 역할 토큰 (앞뒤 공백 포함해 제거)
    private static final java.util.regex.Pattern ROLE_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("\\s*(지음|옮김|엮음|저|글|그림)\\s*");

    private AuthorCharacterUnlockCalculator() {
    }

    /**
     * 알라딘 원문 작가 문자열을 정규화한다.
     *
     * <ol>
     *   <li>null/빈/공백 → {@code ""}</li>
     *   <li>괄호 역할군 {@code (지은이)}/{@code (옮긴이)}/{@code (그림)} 등 모두 제거</li>
     *   <li>괄호 밖 역할 토큰 {@code 지음}/{@code 옮김}/{@code 엮음}/{@code 저} 제거</li>
     *   <li>모든 공백 제거 + strip</li>
     *   <li>결과가 빈 문자열이면 {@code ""} 반환(N-055 — 빈은 절대 매칭 불가)</li>
     * </ol>
     *
     * @param raw 알라딘 원문 작가 문자열(null 허용)
     * @return 정규화된 순수 작가명 문자열(null 없음 — 빈 문자열로 대체)
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = PAREN_PATTERN.matcher(raw).replaceAll("");
        s = ROLE_TOKEN_PATTERN.matcher(s).replaceAll("");
        s = s.replaceAll("\\s+", "").strip();
        return s;
    }

    /**
     * 완독책 작가 목록을 정규화해 집합으로 만든다.
     *
     * <p>빈 문자열로 정규화된 항목은 제외한다(N-055 미완성 메타 누수 가드 — 정규화 결과가 빈
     * 문자열이면 모든 캐릭터의 matchName에 contains되는 사고를 막는다).
     *
     * @param finishedAuthors 완독책 {@code Book.author} 목록(null 원소 허용 — 수동 등록 책)
     * @return 정규화된 작가명 집합. 빈 입력이면 빈 집합.
     */
    public static Set<String> normalizedAuthors(List<String> finishedAuthors) {
        Set<String> result = new LinkedHashSet<>();
        for (String raw : finishedAuthors) {
            String normalized = normalize(raw);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    /**
     * 카탈로그 각 캐릭터의 보유 여부를 판정해 도감 칸으로 돌려준다(카탈로그 순서 유지).
     *
     * <p>판정: 보유 작가 집합 중 하나라도 정규화된 {@code matchName}을 {@code contains}하면 owned.
     * {@code matchName}이 빈 문자열이면 절대 매칭 안 됨(빈 contains=항상참 사고 차단).
     *
     * @param catalog          작가 캐릭터 카탈로그(진열 순서)
     * @param normalizedAuthors 완독책 작가 정규화 집합({@link #normalizedAuthors} 결과)
     * @return 캐릭터별 보유 상태(카탈로그 순서 유지)
     */
    public static List<AuthorCharacterState> resolve(List<AuthorCharacter> catalog,
                                                     Set<String> normalizedAuthors) {
        return catalog.stream()
                .map(character -> {
                    String normalizedMatch = normalize(character.getMatchName());
                    // 빈 matchName은 절대 매칭 불가(빈 문자열은 모든 문자열에 contains되는 사고 차단)
                    if (normalizedMatch.isEmpty()) {
                        return new AuthorCharacterState(character, false);
                    }
                    boolean owned = normalizedAuthors.stream()
                            .anyMatch(a -> a.contains(normalizedMatch));
                    return new AuthorCharacterState(character, owned);
                })
                .toList();
    }
}
