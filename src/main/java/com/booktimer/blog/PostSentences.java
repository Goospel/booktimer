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
