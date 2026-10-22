package com.booktimer.writing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 글쓰기 AI에 보내는 재료 — 그 책의 여백을 최근순으로 상한까지 담은 것(v1 REQ-12 · §4.1).
 *
 * <p>규율은 {@code NoteReference}와 같다 — <b>통째 단위</b>로 넘치는 첫 항목에서 멈추고(중간에서 자르면 거짓 인용이
 * 생긴다) 빠진 개수를 말한다(조용한 누락 금지). 다만 study 패키지를 import하지 않는다: 두 상한은 이제 따로 움직인다.
 *
 * <p>공부 필기·백지복습은 여기에 들어올 길이 없다 — 항목 형이 여백 하나뿐이다(결정 5 · REQ-48).
 *
 * @param included      실리는 여백 — 넘겨받은 순서(최근순) 그대로
 * @param excludedCount 상한에 걸려 빠진 개수 — 화면이 「재료 N개 중 M개를 보냈어요」로 말한다
 * @param chars         여백 블록이 모델 문자열에서 차지하는 글자 수({@link #text}의 「### 여백」부터 끝까지와 같다).
 *                      머리 두 줄([책]·[재료])은 고정 비용이라 세지 않는다
 */
public record WritingMaterial(List<MaterialItem> included, int excludedCount, int chars) {

    /** 상한 — {@code NoteReference.MAX_CHARS}(24,000)에서 출발했지만 따로 둔다. */
    public static final int MAX_CHARS = 24_000;

    /** 생각이 「없는」 것과 같은 값 — 비었거나 공백·문장부호뿐(「.」 · 「 」 · 「…」 — REQ-97). */
    private static final Pattern NO_THOUGHT = Pattern.compile("[\\p{P}\\p{Z}\\s]*");

    /** 앞에서부터 상한까지 — 넘치는 항목이 나오면 거기서 멈추고 뒤의 작은 항목을 끼워 넣지 않는다. */
    public static WritingMaterial select(List<MaterialItem> recentFirst, int max) {
        List<MaterialItem> included = new ArrayList<>();
        int chars = 0;
        int excluded = 0;
        for (MaterialItem item : recentFirst) {
            int cost = block(item).length();
            if (excluded > 0 || chars + cost > max) {
                excluded++;
                continue;
            }
            included.add(item);
            chars += cost;
        }
        return new WritingMaterial(List.copyOf(included), excluded, chars);
    }

    /**
     * 모델이 받는 [책]·[재료] 블록(v1 §10.2 user 블록 1 — 독서 기록 줄 없음).
     *
     * @param book {@code null}이면 책이 풀린 글이다 — 재료 없이 글만 본다는 한 줄
     */
    public String text(BookLine book) {
        if (book == null) {
            return "[책] 없음 — 재료 없이 글만 봅니다\n";
        }
        StringBuilder text = new StringBuilder("[책] ").append(book.title());
        if (book.author() != null && !book.author().isBlank()) {
            text.append(" / ").append(book.author());
        }
        text.append("\n[재료 — 사용자가 이 책을 읽으며 남긴 것. ").append(included.size())
                .append("개(전체 ").append(included.size() + excludedCount).append("개 중 최근 것부터)]\n");
        included.forEach(item -> text.append(block(item)));
        return text.toString();
    }

    /** 여백 하나 — 비용({@link #select})과 조립({@link #text})이 같은 이 함수를 지난다(chars 잠금). */
    private static String block(MaterialItem item) {
        String block = "### 여백 · " + item.date() + "\n옮긴 문장: " + (item.quote() == null ? "없음" : item.quote()) + "\n";
        if (item.text() != null && !NO_THOUGHT.matcher(item.text()).matches()) {
            block += "메모: " + item.text() + "\n";
        }
        return block + "\n";
    }
}
