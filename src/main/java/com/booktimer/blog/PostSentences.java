package com.booktimer.blog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 한 글의 배치 + 문장 행(산 것 · 죽은 것 전부) — 주소 해석 · 박스 구간(B1b {@code QuoteRange})이 받는다. */
public final class PostSentences {

    private final Layout layout;
    private final Map<String, BlogSentence> bySid = new HashMap<>();

    public PostSentences(Layout layout, List<BlogSentence> rows) {
        this.layout = layout;
        rows.forEach(r -> bySid.put(r.getSid(), r));
    }

    public Layout layout() {
        return layout;
    }

    /** 이 글의 문장(산 · 죽은)이면 그 행. */
    public Optional<BlogSentence> get(String sid) {
        return Optional.ofNullable(bySid.get(sid));
    }

    /** 합침 사슬을 끝까지 따라간 산 문장 — 끝이 묘비(또는 고리)면 empty. 산 문장이면 그 자신. */
    public Optional<BlogSentence> resolve(String sid) {
        Set<String> seen = new HashSet<>();
        BlogSentence s = bySid.get(sid);
        while (s != null && !s.live() && s.getMergedInto() != null && seen.add(s.getSid())) {
            s = bySid.get(s.getMergedInto());
        }
        return s != null && s.live() ? Optional.of(s) : Optional.empty();
    }

    /**
     * 댓글 묶음의 끝(B2a §6.3) — 죽은 행이 이 글의 행으로 합쳐졌으면 따라간다(고리면 멈춤). 멈춘 행의 sid — 산 문장 · 묘비 · 이 글
     * 밖으로 합쳐진 행이면 그 자신. {@link #resolve}와 달리 묘비에서도 끝을 낸다(지운 문장의 댓글은 그 옛 주소로 부르면 보인다).
     */
    String end(String sid) {
        Set<String> seen = new HashSet<>();
        BlogSentence s = bySid.get(sid);
        while (s != null && !s.live() && s.getMergedInto() != null && bySid.containsKey(s.getMergedInto()) && seen.add(s.getSid())) {
            s = bySid.get(s.getMergedInto());
        }
        return s == null ? sid : s.getSid();
    }

    /** 끝이 {@code sid}의 끝과 같은 이 글의 sid 전부 — 이 글의 행이 아니면 빈 묶음. */
    Set<String> group(String sid) {
        if (!bySid.containsKey(sid)) return Set.of();
        String target = end(sid);
        Set<String> out = new HashSet<>();
        for (String x : bySid.keySet()) {
            if (end(x).equals(target)) out.add(x);
        }
        return out;
    }

    /** 산 문장 — 배치 순서. */
    public List<BlogSentence> live() {
        List<BlogSentence> out = new ArrayList<>();
        for (String sid : layout.sidsInOrder()) {
            BlogSentence s = bySid.get(sid);
            if (s != null && s.live()) out.add(s);
        }
        return out;
    }

    /** 문장 행 전부(순서 없음) — {@code QuoteRange}가 나뉜 조각(split_from)을 찾는다. */
    Collection<BlogSentence> rows() {
        return Collections.unmodifiableCollection(bySid.values());
    }

    public long maxRowId() {
        return bySid.values().stream().mapToLong(BlogSentence::getId).max().orElse(0);
    }
}
