package com.booktimer.book;

import com.booktimer.personality.WriterName;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 책 ↔ 뉴스 기사 매칭 규칙 (정적 순수 함수).
 *
 * <p><b>계약</b>: <b>기사 제목</b>에 책 제목이 있고 AND 저자명도 있어야 채택한다. 검색 쿼리만으로는
 * 『총, 균, 쇠』처럼 제목이 일반명사 조합인 책이 엉뚱한 기사를 무는데, 그 오탐을 이 AND가 막는다
 * (실측 오탐: 「웨이코 그룹 데미안 월튼 사장 주식 매각」·「삼겹살 사피엔스」).
 * 재현율(놓침)을 조금 내주고 정밀도를 사는 의도된 트레이드오프다.
 *
 * <p><b>본문을 안 보는 이유</b>: 구글 뉴스 RSS의 {@code <description>}은 구글 리다이렉트 링크가 든
 * {@code <a>} 태그뿐이라 매칭 근거가 없다(실측 2026-08-14). 기사 제목만으로 판정한다.
 *
 * <p>비교 전에 HTML 태그·엔티티를 벗기고({@link #clean}), 대소문자와
 * 공백 변형("총, 균, 쇠" ↔ "총,균,쇠")을 흡수한다.
 *
 * <p>ponytail: 번역서 표기 흔들림("재레드/재러드")은 저자 문자열 완전 포함으로는 놓친다 — 알려진 천장.
 * 성(마지막 토큰) 폴백은 실측 후 기각했다 — 채택이 32→37건 느는 데 그치는데 오탐 위험만 커진다.
 */
public final class BookNewsMatcher {

    /** 부제 구분자 ' - '(하이픈·en dash·em dash) 뒤 전부. 공백에 둘러싸일 때만 — "e-book"의 하이픈은 제목의 일부다. */
    private static final Pattern SUBTITLE = Pattern.compile("\\s[-–—]\\s.*$");

    /** 괄호 블록("(개정판)"·"(양장본)" 등). 반각 ()·전각 （） 모두. */
    private static final Pattern PAREN_BLOCK = Pattern.compile("[(（][^)）]*[)）]");

    /** 끝에 붙은 판매단위 꼬리 — "세트"·"전3권"·"3권"·"상"·"하". 여러 개가 겹쳐 붙어도 한 번에 뗀다. */
    private static final Pattern VOLUME_TAIL = Pattern.compile("(?:\\s(?:세트|전\\s?\\d+권|\\d+권|상|하))+$");

    private BookNewsMatcher() {
    }

    /**
     * 책 제목을 매칭·검색용으로 정규화한다 — 알라딘 원문에 붙는 <b>부제·괄호·판매단위</b>를 뗀다.
     *
     * <p><b>왜</b>: {@code Book.title}은 알라딘 API 원문이라 "코스모스 - 특별판"·"안나 카레니나 세트 - 전3권"
     * 같은 꼴이다. 기사 제목에 그 문자열이 통째로 들어 있을 리 없어, 원문 그대로 AND 매칭하면 전 종목이
     * 기각된다(운영 실측 2026-08-14: 대상 29종 · 저장 0건). 검색 질의도 같은 이유로 나빠진다.
     *
     * <p>순서: {@link #clean} → ' - ' 뒤 부제 절단 → 괄호 블록 제거 → 판매단위 꼬리 제거.
     * null·정규화 결과가 빈 값(제목이 통째로 괄호였던 병리적 입력)은 빈 문자열 — 호출부가 수집 대상에서 뺀다.
     */
    public static String normalizeTitle(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        String s = SUBTITLE.matcher(clean(rawTitle)).replaceFirst("");
        s = PAREN_BLOCK.matcher(s).replaceAll(" ").replaceAll("\\s+", " ").strip();
        return VOLUME_TAIL.matcher(s).replaceAll("").strip();
    }

    /**
     * 책 저자를 매칭·검색용으로 정규화한다 — <b>대표 글쓴이 1명</b>만 남긴다
     * ("칼 세이건 (지은이), 홍승수 (옮긴이)" → "칼 세이건").
     *
     * <p>알라딘 author 원문 파싱은 {@link WriterName#lead}가 이미 한다(책BTI 집계용) — 역할 괄호 제거 +
     * 옮긴이·그림 등 비저자 역할 건너뛰기. 여기서 다시 짜지 않고 그대로 쓴다.
     * 글쓴이가 없으면(역자만 있는 원문) 빈 문자열 — 호출부가 수집 대상에서 뺀다.
     */
    public static String normalizeAuthor(String rawAuthor) {
        String lead = WriterName.lead(clean(rawAuthor));
        return lead == null ? "" : lead;
    }

    /**
     * 기사가 이 책의 것인지 판정한다 — <b>정규화한</b> 제목 AND 저자.
     *
     * <p>비교 전에 {@link #normalizeTitle}·{@link #normalizeAuthor}로 알라딘 원문의 부제·역할표기를 뗀다.
     * 정규화 결과가 비면 AND를 만들 수 없어 항상 기각한다(수집 대상 선별에서도 같은 이유로 제외되지만,
     * 여기서도 방어한다).
     */
    public static boolean matches(String articleTitle, String bookTitle, String bookAuthor) {
        String title = normalizeTitle(bookTitle);
        String author = normalizeAuthor(bookAuthor);
        if (title.isEmpty() || author.isEmpty()) {
            return false;
        }
        String haystack = key(nullToEmpty(articleTitle));
        return haystack.contains(key(title)) && haystack.contains(key(author));
    }

    /**
     * 표시용 정규화 — HTML 태그를 벗기고 엔티티를 해제한 뒤 공백을 하나로 줄인다.
     * 대소문자는 <b>보존</b>한다(저장·노출되는 기사 제목이 이 값이다). null은 null 그대로.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("<[^>]*>", "");
        // &amp;는 마지막에 — 먼저 풀면 "&amp;quot;"가 두 번 해제된다.
        s = s.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return s.replaceAll("\\s+", " ").strip();
    }

    /** 비교용 키 — {@link #clean} + 소문자 + 공백 제거(공백 유무 변형을 흡수). */
    private static String key(String raw) {
        return clean(raw).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
