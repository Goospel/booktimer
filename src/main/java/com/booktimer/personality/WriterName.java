package com.booktimer.personality;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 알라딘 {@code author} 원문에서 대표 '글쓴이(저자)' 1명만 뽑는다 — 책BTI 집계 전용.
 *
 * <p>알라딘 author는 {@code "이름 (역할), 이름 (역할)…"} 형식이다(예:
 * {@code "레프 니콜라예비치 톨스토이 (지은이), 연진희 (옮긴이)"}). '자주 읽는 저자'·'저자 충성도'는
 * 글을 쓴 사람만 세야 하므로, <b>옮긴이·그림·사진·엮음·감수 등 비저자 역할을 제외</b>하고
 * 글쓴이(지은이·글·저·원작 등) 또는 역할 표기가 없는 이름만 채택한다.
 *
 * <p>검색·책 상세의 author 표시는 원문을 그대로 쓴다(거기선 옮긴이 정보가 유용) — 이 정제는
 * 책BTI 집계({@link ReadingProfileAggregator}·{@link PersonalityTagAttribution})에서만 쓴다.
 *
 * <p>서재 작가 캐릭터의 {@link com.booktimer.garden.AuthorCharacterUnlockCalculator#normalize}와는
 * 목적이 다르다 — 그쪽은 옮긴이 이름을 남기고 공백까지 지워 contains 매칭에 쓴다. 여기선 옮긴이를
 * 떼고 표시 가능한 이름(내부 공백 보존)을 돌려준다.
 */
public final class WriterName {

    /** 괄호 역할 어노테이션 "(지은이)" 등 — 그룹1이 역할 텍스트. 반각 ()·전각 （） 모두 처리(역할이 이름에 남지 않게). */
    private static final Pattern PAREN = Pattern.compile("[(（]([^)）]*)[)）]");

    /**
     * 글쓴이로 인정할 역할 키워드. 역할 텍스트에 하나라도 포함되면 글쓴이로 본다(복합 역할 "글·그림"도
     * "글" 포함 → 글쓴이). 역할 표기가 아예 없는 토큰도 글쓴이로 간주한다(수동 입력·단순 표기 호환).
     * 옮긴이·그림·사진·엮음·감수·해설·기획·번역 등은 이 목록에 없어 자연히 제외된다.
     */
    private static final List<String> WRITER_ROLE_KEYWORDS =
            List.of("지은이", "지음", "글", "저", "원작", "원저");

    private WriterName() {
    }

    /**
     * author 원문에서 대표 글쓴이 1명을 추출한다.
     *
     * <p>author를 쉼표로 토큰 분리한 뒤 <b>첫 번째 '글쓴이' 토큰</b>의 이름을 돌려준다 — 옮긴이가 앞서
     * 와도 글쓴이를 찾을 때까지 건너뛴다. 공저는 대표 1명(첫 글쓴이)만 센다(한 책 = 1카운트 유지).
     *
     * @param rawAuthor 알라딘 author 원문(null 허용)
     * @return 첫 번째 글쓴이 이름(괄호 역할 제거, 내부 공백 보존). 글쓴이가 없으면 null
     *         (옮긴이·그림 등 비저자만 있거나 null/빈 입력 — 분포에서 제외)
     */
    public static String lead(String rawAuthor) {
        if (rawAuthor == null) {
            return null;
        }
        for (String token : rawAuthor.split(",")) {
            if (!isWriterRole(roleText(token))) {
                continue;
            }
            String name = nameOf(token);
            if (!name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    /** 토큰 안 모든 괄호 내용을 이어 붙인 역할 텍스트(괄호 없으면 ""). */
    private static String roleText(String token) {
        StringBuilder sb = new StringBuilder();
        Matcher m = PAREN.matcher(token);
        while (m.find()) {
            sb.append(m.group(1));
        }
        return sb.toString();
    }

    /** 역할 텍스트가 글쓴이인가 — 표기 없음(빈) 또는 글쓴이 키워드 포함. */
    private static boolean isWriterRole(String roles) {
        if (roles.isBlank()) {
            return true;
        }
        return WRITER_ROLE_KEYWORDS.stream().anyMatch(roles::contains);
    }

    /** 토큰에서 괄호 역할군을 떼고 이름만 — 내부 다중 공백은 단일화, 양끝 strip. */
    private static String nameOf(String token) {
        String name = PAREN.matcher(token).replaceAll("");
        return name.replaceAll("\\s+", " ").strip();
    }
}
