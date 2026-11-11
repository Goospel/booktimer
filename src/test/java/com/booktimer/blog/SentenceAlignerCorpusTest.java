package com.booktimer.blog;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U-136 — 판 비교 문턱이 시험 글 48건에서 맞는가(계획 2026-11-11-b1-blog-core §8.1 3 · §9 · 설계 원장 「오판 ≤ 1 · 나뉨 모두 split_from」).
 *
 * <p>라벨({@code src/test/resources/blog/u136-corpus.txt})은 정렬기 구현 전에 따로 커밋했다 — 결과에 맞춰 고치지 않는다.
 * 오판이 2건 이상이거나 나뉨 기록이 하나라도 빠지면 문턱 · 라벨을 고치지 말고 멈춘다(§8 드리프트 규칙).
 * 통과 줄 「U-136 통과 — 오판 n/48」은 단언을 모두 지난 뒤에만 찍힌다(U-136 EXPECT — 실패하면 나오지 않는다).
 */
class SentenceAlignerCorpusTest {

    record Case(String id, List<String> old, List<String> neu, List<String> expected) {
    }

    @Test
    void U_136_시험_글_48건에서_오판_1건_이하_나뉨_기록_누락_0() throws IOException {
        List<Case> cases = load();
        assertThat(cases).hasSize(48);

        List<String> misjudged = new ArrayList<>();
        List<String> splitMissing = new ArrayList<>();
        for (Case c : cases) {
            List<String> actual = tokens(SentenceAligner.align(SentenceAlignerTest.olds(c.old()), c.neu()));
            if (actual.equals(c.expected())) continue;
            misjudged.add(c.id() + " 기대 " + c.expected() + " · 실제 " + actual);
            for (int j = 0; j < c.expected().size(); j++) {
                if (c.expected().get(j).endsWith("/") && !c.expected().get(j).equals(actual.get(j))) {
                    splitMissing.add(c.id() + " 새 문장 " + (j + 1));
                }
            }
        }

        assertThat(splitMissing).as("나뉨 기록 누락 — 오판 %s", misjudged).isEmpty();
        assertThat(misjudged).as("오판").hasSizeLessThanOrEqualTo(1);
        System.out.println("U-136 통과 — 오판 " + misjudged.size() + "/48");
        misjudged.forEach(m -> System.out.println("U-136 오판 사례: " + m));
    }

    /** 라벨 파일과 같은 토큰으로 — 합침은 묶음 · 나뉨은 조각이면 어느 쪽이 주소를 잇든 같은 토큰이다(라벨 머리 주석). */
    static List<String> tokens(SentenceAligner.Alignment a) {
        List<String> out = new ArrayList<>();
        for (int j = 0; j < a.sidOfNew().size(); j++) {
            String sid = a.sidOfNew().get(j);
            if (sid == null) {
                String from = a.splitFrom().get(j);
                out.add(from == null ? "+" : from + "/");
                continue;
            }
            TreeSet<String> group = new TreeSet<>(Comparator.comparingInt((String s) -> Integer.parseInt(s.substring(1))));
            group.add(sid);
            a.merged().forEach((o, t) -> {
                if (t.equals(sid)) group.add(o);
            });
            if (group.size() > 1) out.add(String.join("+", group));
            else if (a.splitFrom().containsValue(sid)) out.add(sid + "/");
            else out.add(a.edited().contains(sid) ? sid + "~" : sid);
        }
        return out;
    }

    static List<Case> load() throws IOException {
        List<Case> cases = new ArrayList<>();
        try (InputStream in = SentenceAlignerCorpusTest.class.getResourceAsStream("/blog/u136-corpus.txt")) {
            assertThat(in).as("u136-corpus.txt").isNotNull();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String id = null;
            List<String> old = new ArrayList<>(), neu = new ArrayList<>();
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                line = line.stripTrailing();
                if (line.startsWith("== ")) {
                    id = line.substring(3).split(" ")[0];
                    old = new ArrayList<>();
                    neu = new ArrayList<>();
                } else if (line.startsWith("- ")) {
                    old.add(line.substring(2));
                } else if (line.startsWith("+ ")) {
                    neu.add(line.substring(2));
                } else if (line.startsWith("= ")) {
                    List<String> expected = Arrays.stream(line.substring(2).trim().split(" +")).collect(Collectors.toList());
                    assertThat(expected).as(id + " 토큰 수").hasSameSizeAs(neu);
                    cases.add(new Case(id, List.copyOf(old), List.copyOf(neu), expected));
                }
            }
        }
        return cases;
    }
}
