package com.booktimer.blog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 블로그 글을 문단 → 줄 → 문장으로 나눈다(설계 J12 · 계획 2026-11-11-b1-blog-core §6.1 — 서버에서만, 앱은 나누지 않는다).
 *
 * <p>JDK {@code BreakIterator}는 따옴표 뒤 조사 · 띄어 쓴 날짜 · 줄 머리 번호 · 영문 약어에서 깨진다(§6.1 표의 대조 열)라
 * 규칙을 손으로 둔다. 줄이 바뀌는 자리가 곧 줄바꿈(br)이다.
 */
public final class SentenceSplitter {

    /** 한 문장의 UTF-16 길이 상한 — 넘으면 자른다(§6.1 6). */
    static final int MAX = 1000;

    public record Paragraph(List<Line> lines) {
    }

    public record Line(List<String> sentences) {
    }

    private static final String TERM = ".?!…";
    private static final String CLOSERS = "\"'”’」』)]》〉";
    // `no`는 넣지 않는다 — 「I said no. Then I left.」가 붙는다(§4.3 · C06 천장)
    private static final Set<String> ABBR = Set.of("dr", "mr", "mrs", "ms", "prof", "st", "jr", "sr", "vs", "etc", "e.g", "i.e", "u.s", "u.k");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[\\s\\u00A0\\u3000]*\\n");
    private static final Pattern DATE_END = Pattern.compile("\\d{4}\\.\\s*\\d{1,2}\\.\\s*\\d{1,2}\\.$");
    private static final Pattern LIST_HEAD = Pattern.compile("^\\d{1,3}\\.\\s");

    private SentenceSplitter() {
    }

    /** 공백 술어 하나(§6.1 0) — {@code Character.isWhitespace}는 U+00A0을 거짓으로 본다. 경계 · 걷기 · 판 비교가 함께 쓴다. */
    static boolean space(int c) {
        return Character.isWhitespace(c) || c == 0xA0;
    }

    /** 앞뒤 공백 떼기 — {@link String#strip()}은 U+00A0을 걷지 않아 쓰지 않는다(T19). */
    static String strip(String s) {
        int a = 0, b = s.length();
        while (a < b && space(s.charAt(a))) a++;
        while (b > a && space(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    public static List<Paragraph> split(String body) {
        List<Paragraph> out = new ArrayList<>();
        if (body == null) return out;
        String text = body.replace("\r\n", "\n").replace('\r', '\n');
        for (String block : PARAGRAPH_BREAK.split(text)) {
            List<Line> lines = new ArrayList<>();
            for (String raw : block.split("\n")) {
                String line = strip(raw);
                if (!line.isEmpty()) lines.add(new Line(splitLine(line)));
            }
            if (!lines.isEmpty()) out.add(new Paragraph(List.copyOf(lines)));
        }
        return out;
    }

    private static List<String> splitLine(String line) {
        List<String> pieces = new ArrayList<>();
        int n = line.length(), start = 0, i = 0;
        while (i < n) {
            if (TERM.indexOf(line.charAt(i)) < 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && TERM.indexOf(line.charAt(j)) >= 0) j++;
            int k = j;
            while (k < n && CLOSERS.indexOf(line.charAt(k)) >= 0) k++;
            if (k < n && space(line.charAt(k)) && !exception(line, start, i, j, k)) {
                pieces.add(line.substring(start, k));
                start = k;
            }
            i = j;
        }
        pieces.add(line.substring(start));

        List<String> out = new ArrayList<>();
        for (String piece : pieces) {
            String s = strip(piece);
            while (s.length() > MAX) {
                int cut = s.lastIndexOf(' ', MAX);
                if (cut <= 0) cut = Character.isHighSurrogate(s.charAt(MAX - 1)) ? MAX - 1 : MAX;
                out.add(strip(s.substring(0, cut)));
                s = strip(s.substring(cut));
            }
            if (!s.isEmpty()) out.add(s);
        }
        return List.copyOf(out);
    }

    /** 경계 후보 — [i, j) 마침표 무리 · [j, k) 닫는 부호 · k는 공백. 예외(나누지 않음)면 참(§6.1 5). */
    private static boolean exception(String line, int start, int i, int j, int k) {
        if (j - i != 1 || line.charAt(i) != '.') return false;
        // ① 띄어 쓴 숫자 마침표 — 앞이 숫자이고 공백 뒤 첫 글자가 숫자
        int m = k;
        while (m < line.length() && space(line.charAt(m))) m++;
        if (i > 0 && Character.isDigit(line.charAt(i - 1)) && m < line.length() && Character.isDigit(line.charAt(m))) return true;
        // ② 띄어 쓴 날짜(YYYY. M. D.)의 끝 마침표
        if (DATE_END.matcher(line.substring(0, j)).find()) return true;
        // ③ 줄 머리 목록 번호의 점
        Matcher head = LIST_HEAD.matcher(line);
        if (start == 0 && head.find() && head.end() - 2 == i) return true;
        // ④ 바로 앞 낱말(ASCII 글자와 점)이 약어
        int t = i - 1;
        while (t >= 0 && (line.charAt(t) < 128 && Character.isLetter(line.charAt(t)) || line.charAt(t) == '.')) t--;
        return ABBR.contains(line.substring(t + 1, i).toLowerCase(Locale.ROOT));
    }
}
