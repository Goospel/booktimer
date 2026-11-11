package com.booktimer.blog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 인용 박스 구간 — 구성원 sid가 원 글의 지금 배치에서 어디까지인지(계획 2026-11-11-b1-blog-core §6.5 · §15.3 ⑦ · J70~J72).
 * 순수 규칙: 저장소 · 시계 없음. 「가능」 판정은 {@link #updatable}, 반영 조건(1,000자 · 차단)은 부르는 쪽 몫이다.
 */
public final class QuoteRange {

    private QuoteRange() {
    }

    /**
     * @param sids       지금 구간(처음 ~ 끝 사이 문장 전부 — 새로 끼운 문장 포함)
     * @param text       이은 글({@link #join})
     * @param allDeleted 산 문장에 하나도 닿지 않음(또는 글 없음)
     * @param crossesBox 구간 사이에 인용 박스가 있다
     * @param viaMerge   닿은 산 문장 가운데 합침 사슬로 닿은 것이 있다(주소 {@code MERGED})
     */
    public record Current(List<String> sids, String text, boolean allDeleted, boolean crossesBox, boolean viaMerge) {
    }

    private static final Current DELETED = new Current(List.of(), "", true, false, false);

    /** 배치를 편 한 칸 — 박스면 {@code sid} null. */
    private record Slot(String sid, int item, int line) {
        boolean box() {
            return sid == null;
        }
    }

    /**
     * 지금 구간. {@code seenSentenceId} = 인용(반영) 때 원 글의 가장 큰 행 id — 그보다 큰 id의 나뉜 조각만 구간에 든다(§4.6).
     * 주소 해석은 {@code null}(조각을 더하지 않는다 — J72). {@code post}가 null이면 글 지움.
     */
    public static Current current(List<String> members, PostSentences post, Long seenSentenceId) {
        if (post == null) return DELETED;
        Map<String, List<BlogSentence>> fragmentsOf = new HashMap<>();
        for (BlogSentence r : post.rows()) {
            if (r.getSplitFrom() != null) fragmentsOf.computeIfAbsent(r.getSplitFrom(), k -> new ArrayList<>()).add(r);
        }
        // 사슬 닫힘(worklist) — 죽은 행은 merged_into로, 인용 뒤 조각(id > seen)은 split_from으로 끝까지(R18)
        Set<String> roots = new LinkedHashSet<>();
        Set<String> mergedReach = new HashSet<>();
        Deque<String> work = new ArrayDeque<>();
        for (String m : members) {
            if (post.get(m).isPresent() && roots.add(m)) work.add(m);
        }
        while (!work.isEmpty()) {
            BlogSentence r = post.get(work.poll()).orElseThrow();
            String into = r.getMergedInto();
            if (!r.live() && into != null && post.get(into).isPresent()) {
                mergedReach.add(into); // 이미 구성원이어도 「합침으로 닿음」(V1)
                if (roots.add(into)) work.add(into);
            }
            if (seenSentenceId == null) continue;
            for (BlogSentence f : fragmentsOf.getOrDefault(r.getSid(), List.of())) {
                if (f.getId() > seenSentenceId && roots.add(f.getSid())) work.add(f.getSid());
            }
        }
        Set<String> hit = roots.stream().filter(s -> post.get(s).orElseThrow().live()).collect(Collectors.toSet());

        List<Slot> slots = slots(post.layout());
        int lo = -1;
        int hi = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).box() && hit.contains(slots.get(i).sid())) {
                if (lo < 0) lo = i;
                hi = i;
            }
        }
        if (lo < 0) return DELETED;

        List<String> sids = new ArrayList<>();
        List<List<String>> lines = new ArrayList<>();
        boolean box = false;
        Slot prev = null;
        for (int i = lo; i <= hi; i++) {
            Slot s = slots.get(i);
            if (s.box()) {
                box = true;
            } else {
                if (prev == null || prev.box() || prev.item() != s.item() || prev.line() != s.line()) lines.add(new ArrayList<>());
                lines.get(lines.size() - 1).add(post.get(s.sid()).orElseThrow().getText());
                sids.add(s.sid());
            }
            prev = s;
        }
        boolean viaMerge = hit.stream().anyMatch(mergedReach::contains);
        return new Current(List.copyOf(sids), join(lines), false, box, viaMerge);
    }

    /** 「업데이트 있음」(§6.5 판정) — 산 문장에 닿고 이은 글이 스냅샷과 {@code norm}으로 다르다(공백 차이는 잡음이 아니다 · §4.10). */
    public static boolean updatable(Current c, String snapshot) {
        return !c.allDeleted() && !SentenceAligner.norm(c.text()).equals(SentenceAligner.norm(snapshot));
    }

    /**
     * 만들기의 구간 — {@code fromSid} · {@code toSid}가 지금 배치의 산 문장인지는 부르는 쪽이 먼저 잰다(400 검사는 부르는 쪽 몫 ·
     * 배치에 없는 sid는 불변식 위반이라 500). 순서가 뒤집혀 와도 처음 ~ 끝. 사이에 박스가 있으면 empty.
     * 이은 글은 {@code current(이 목록, post, null).text()}로 얻는다(이음 규칙은 한 벌).
     */
    public static Optional<List<String>> between(PostSentences post, String fromSid, String toSid) {
        List<Slot> slots = slots(post.layout());
        int a = -1;
        int b = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (fromSid.equals(slots.get(i).sid())) a = i;
            if (toSid.equals(slots.get(i).sid())) b = i;
        }
        if (a < 0 || b < 0) throw new IllegalStateException("배치에 없는 문장: " + fromSid + "-" + toSid);
        List<String> out = new ArrayList<>();
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            if (slots.get(i).box()) return Optional.empty();
            out.add(slots.get(i).sid());
        }
        return Optional.of(List.copyOf(out));
    }

    /**
     * 이음(REQ-247) — 줄 안의 문장은 공백 하나, 줄 · 문단 · 박스가 바뀌면 {@code \n} 하나. 구분자는 늘 한 글자라
     * 글자 수 = 문장 글자 합 + 문장 수 − 1(UTF-16). 쪽지 생각 · MARGIN 박스도 이 함수로 잇는다.
     */
    public static String join(List<List<String>> lines) {
        return lines.stream().map(l -> String.join(" ", l)).collect(Collectors.joining("\n"));
    }

    private static List<Slot> slots(Layout layout) {
        List<Slot> out = new ArrayList<>();
        List<Layout.Item> items = layout.items();
        for (int i = 0; i < items.size(); i++) {
            List<List<String>> p = items.get(i).p();
            if (p == null) {
                out.add(new Slot(null, i, -1));
                continue;
            }
            for (int l = 0; l < p.size(); l++) {
                for (String sid : p.get(l)) out.add(new Slot(sid, i, l));
            }
        }
        return out;
    }
}
