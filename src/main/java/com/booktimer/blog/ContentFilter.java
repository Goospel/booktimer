package com.booktimer.blog;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 걸러내기(D18 · J49 · 계획 2026-11-16-blog-b2 §4.2 · §6.1 · §6.2) — 글 · 댓글 · 이름이 한 곳을 지난다. 순수 · 상태 0.
 *
 * <p>검사는 NFKC로 접은 <b>사본</b>에서 한다(저장값 · 보여 주는 값은 그대로) — NFD로 풀어 쓴 한글 · 전각 · 수학 글자 · 동그라미
 * 글자 · 반각 자모가 원래 낱말로 접힌다. 방향 글자와 이름의 보이지 않는 글자만 원래 코드 포인트를 본다.
 *
 * <p>천장(일부러 막지 않는 것): 띄운 우회(「시 발」 — 「오늘 시 발표」를 살리려고 띄어쓰기를 지우지 않는다) · 자모 분해
 * (「ㅅㅣㅂㅏㄹ」 — NFKC 뒤 「시바ᄅ」) · 닮은 글자(키릴 · 그리스 — NFKC가 접지 않는다) · 맨 도메인(「bit.ly/…」 — 「3.5점」과
 * 가를 규칙이 없다). 뒤는 신고 · 운영자 가리기가 받친다.
 */
public final class ContentFilter {

    public enum Kind { NAME, COMMENT, POST }

    static final String BANNED_MESSAGE = "이 표현은 올릴 수 없어요";
    static final String INVISIBLE_MESSAGE = "이름에 줄바꿈이나 보이지 않는 글자는 쓸 수 없어요";
    private static final String REPEAT_MESSAGE = "같은 글자를 너무 많이 이어 썼어요";
    private static final String DIRECTION_MESSAGE = "글자 방향을 바꾸는 보이지 않는 글자는 쓸 수 없어요";

    static final int COMMENT_LINKS = 3;
    static final int POST_LINKS = 20;
    static final int REPEAT = 30;

    /** {@code https://www.}는 링크 하나다 — 앞 갈래가 {@code www.}까지 먹는다. */
    private static final Pattern LINK = Pattern.compile("(?i)https?://(?:www\\.)?|www\\.");
    /** 빈칸처럼 보이는 글자(한글 채움 · 반각 채움 · 점자 빈칸). */
    private static final Set<Integer> BLANK_LOOKING = Set.of(0x3164, 0x115F, 0x1160, 0xFFA0, 0x2800);

    /**
     * 금지어 — 욕설 · 성적 비하 · 혐오 · 비하어 · 초성 축약 · 영어. 아래 {@link #fold}를 거쳐 들고 있는다(첫소리 자모 「ㅅㅂ」은 NFKC
     * 뒤 다른 코드 포인트가 되므로 상수도 같은 정규화를 거쳐야 사용자 글과 맞는다). 일상어와 겹치는 낱말(「보지」 · 「자지」 —
     * 「보지 않았다」 · 「새끼」 — 새끼손가락 · 「씹」 — 씹다)은 일부러 뺐다.
     */
    private static final List<String> BANNED = folded(List.of(
            // 욕설
            "시발", "씨발", "씨바", "씨빨", "씨팔", "시팔", "쓰발", "씹새", "씹년", "씹창", "씹할", "십새끼", "개새끼", "개새기",
            "개색기", "개색끼", "개세끼", "개쉐이", "좆", "존나", "병신", "븅신", "빙신", "지랄", "염병", "엠창", "니기미", "니미럴",
            "느금마", "니애미", "니애비", "애미뒤진", "애비뒤진", "썅", "쌍년", "쌍놈", "호로새끼", "후레자식", "미친년", "미친놈",
            // 성적 비하
            "걸레년", "화냥년", "갈보년", "개보지", "보짓물", "좆물",
            // 혐오 · 비하어
            "김치녀", "된장녀", "한남충", "맘충", "틀딱", "급식충", "짱깨", "쪽바리", "똥남아", "깜둥이", "장애새끼",
            // 초성 축약
            "ㅅㅂ", "ㅆㅂ", "ㅄ", "ㅂㅅ", "ㅈㄹ", "ㅈㄴ", "ㅅㄲ", "ㄱㅅㄲ",
            // 영어
            "fuck", "shit", "bitch", "cunt", "nigger", "nigga", "faggot", "asshole", "motherfucker"));

    /** 예외 단어 — 금지어를 품은 멀쩡한 낱말. 금지어를 보기 전에 공백으로 바꾼다(거짓 양성 시험이 빨개질 때만 더한다). */
    private static final List<String> EXCEPTIONS = folded(List.of("시발점", "시발역"));

    private ContentFilter() {
    }

    /** 막히면 사용자 문구. 갈래마다 처음 걸린 규칙 하나(§6.1 순서). */
    public static Optional<String> violation(String text, Kind kind) {
        if (text == null) {
            return Optional.empty();
        }
        if (kind == Kind.NAME) {
            if (invisibleInName(strip(text))) return Optional.of(INVISIBLE_MESSAGE);
        } else if (text.indexOf(0x202D) >= 0 || text.indexOf(0x202E) >= 0) {
            return Optional.of(DIRECTION_MESSAGE);
        }
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        int links = countLinks(nfkc);
        switch (kind) {
            case NAME -> {
                if (links > 0) return Optional.of("이름에는 링크를 넣을 수 없어요");
            }
            case COMMENT -> {
                if (links > COMMENT_LINKS) return Optional.of("링크는 댓글 하나에 3개까지 넣을 수 있어요");
                if (longestRun(nfkc) >= REPEAT) return Optional.of(REPEAT_MESSAGE);
            }
            case POST -> {
                if (links > POST_LINKS) return Optional.of("링크는 글 하나에 20개까지 넣을 수 있어요");
            }
        }
        return banned(nfkc) ? Optional.of(BANNED_MESSAGE) : Optional.empty();
    }

    /** 앞뒤 공백 떼기(NBSP 포함 — {@link SentenceSplitter#strip}). */
    public static String strip(String s) {
        return SentenceSplitter.strip(s);
    }

    /** 보이는 글자가 하나라도 있나 — 공백 · 서식 · 결합 부호 · 제어 문자 · 빈칸처럼 보이는 글자만이면 거짓. */
    public static boolean hasVisible(String s) {
        return s != null && s.codePoints().anyMatch(ContentFilter::visible);
    }

    private static boolean visible(int cp) {
        int t = Character.getType(cp);
        return !SentenceSplitter.space(cp) && !BLANK_LOOKING.contains(cp)
                && t != Character.NON_SPACING_MARK && t != Character.ENCLOSING_MARK && t != Character.FORMAT
                && t != Character.CONTROL && t != Character.LINE_SEPARATOR && t != Character.PARAGRAPH_SEPARATOR;
    }

    /** §6.2 — 제어 · 줄/문단 구분 · 서식(이모지 사이 ZWJ만 빼고) · 빈칸처럼 보이는 글자가 있거나 보이는 글자가 0이면 참. */
    private static boolean invisibleInName(String s) {
        int[] cps = s.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            int t = Character.getType(cps[i]);
            if (t == Character.CONTROL || t == Character.LINE_SEPARATOR || t == Character.PARAGRAPH_SEPARATOR
                    || BLANK_LOOKING.contains(cps[i])) {
                return true;
            }
            if (t == Character.FORMAT && !(cps[i] == 0x200D && zwjInEmoji(cps, i))) {
                return true;
            }
        }
        return !hasVisible(s);
    }

    /** 앞쪽(이모지 수식 · VS15/16을 건너뛴)과 뒤쪽이 둘 다 그림 문자인 ZWJ. 태그 이모지(잉글랜드 깃발)는 막힌다(천장 — 드물다). */
    private static boolean zwjInEmoji(int[] cps, int i) {
        int p = i - 1;
        while (p >= 0 && (cps[p] == 0xFE0F || cps[p] == 0xFE0E || (cps[p] >= 0x1F3FB && cps[p] <= 0x1F3FF))) p--;
        return p >= 0 && i + 1 < cps.length
                && Character.isExtendedPictographic(cps[p]) && Character.isExtendedPictographic(cps[i + 1]);
    }

    private static int countLinks(String nfkc) {
        Matcher m = LINK.matcher(nfkc);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** 글자({@code isLetter})만 차례로 뽑은 줄에서 같은 코드 포인트가 이어진 가장 긴 길이 — 공백 · 기호 · 서식은 끊지도 세지도 않는다. */
    private static int longestRun(String nfkc) {
        int prev = -1, run = 0, max = 0;
        for (int i = 0; i < nfkc.length(); ) {
            int cp = nfkc.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            run = cp == prev ? run + 1 : 1;
            prev = cp;
            max = Math.max(max, run);
        }
        return max;
    }

    private static boolean banned(String nfkc) {
        String t = fold(nfkc);
        for (String e : EXCEPTIONS) t = t.replace(e, " ");
        for (String b : BANNED) {
            if (t.contains(b)) return true;
        }
        return false;
    }

    /** 금지어 정규화(§6.1) — NFKC → 소문자 → 글자만 남기고 공백은 하나로 · 나머지(숫자 · 기호 · 서식 · 결합 부호)는 지운다. */
    static String fold(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                b.appendCodePoint(cp);
            } else if (SentenceSplitter.space(cp) && b.length() > 0 && b.charAt(b.length() - 1) != ' ') {
                b.append(' ');
            }
        }
        return b.toString().strip();
    }

    private static List<String> folded(List<String> words) {
        return words.stream().map(ContentFilter::fold).toList();
    }
}
