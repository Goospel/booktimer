package com.booktimer.blog;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 올린 판의 배치 — {@code blog_post.layout_json}의 모양(계획 2026-11-11-b1-blog-core §6.3). 문장 순서의 단일 출처다.
 *
 * <p>JSON 꼴: {@code [{"p":[["sidA","sidB"],["sidC"]],"q":null},{"p":null,"q":12}]} — 항목 = 문단(줄 목록 · 줄 = sid 목록 ·
 * 줄이 바뀌는 자리가 곧 br) 또는 인용 박스({@code q} = 박스 label — B1b).
 * shortcut: 1,500개 상한 — TEXT 안에 들게, 넘는 글이 나오면 MEDIUMTEXT로(H2 validate 먼저 확인)
 *
 * @param items 비어 있어도 {@code null}이 아니다
 */
public record Layout(List<Item> items) {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Item>> ITEMS = new TypeReference<>() { };

    /** 문단이면 {@code p}만, 박스면 {@code q}만 있다. */
    public record Item(List<List<String>> p, Long q) {
    }

    public Layout {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** {@code null} · 빈 값 → 빈 배치(첫 올리기). */
    public static Layout parse(String json) {
        if (json == null || json.isBlank()) return new Layout(List.of());
        return new Layout(JSON.readValue(json, ITEMS));
    }

    public String toJson() {
        return JSON.writeValueAsString(items);
    }

    /** 박스를 건너뛴 문장 sid — 배치 순서. */
    public List<String> sidsInOrder() {
        List<String> out = new ArrayList<>();
        for (Item item : items) {
            if (item.p() == null) continue;
            for (List<String> line : item.p()) out.addAll(line);
        }
        return out;
    }

    /** 배치 항목 수 = 문장 + 박스(§6.3 · 1,500개 상한이 센다). */
    public int size() {
        int n = 0;
        for (Item item : items) n += item.p() == null ? 1 : item.p().stream().mapToInt(List::size).sum();
        return n;
    }
}
