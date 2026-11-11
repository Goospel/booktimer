package com.booktimer.blog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 옛 판의 산 문장과 새로 나눈 문장을 견줘 문장 주소(sid)를 잇는다(설계 J13 · J72 · 계획 2026-11-11-b1-blog-core §6.2 · §4.2).
 *
 * <p>순서: ① LCS(공백 정규화가 같은 문장 — 주소 그대로) ② LCS 닻 사이 틈마다 Dice ≥ 0.5 짝(주소 잇고 판+1)
 * ③ 합침(사슬 · 포함도 ≥ 0.6 · 자람 ≥ 옛 문장 절반) ④ 나뉨 기록(거울) ⑤ 옮김(글자 같음) ⑥ 남은 옛 = 묘비 · 남은 새 = 새 주소.
 * 문턱 비교는 모두 정수로 한다(부동소수 경계를 없앤다 — 비평 L3): dice ≥ 0.5 ⇔ {@code 4·|A∩B| ≥ |A|+|B|} ·
 * 포함도 ≥ 0.6 ⇔ {@code 5·|P∩W| ≥ 3·|P|} · 「절반 이상」 ⇔ {@code 2·차이 ≥ 길이}.
 */
public final class SentenceAligner {

    public record Old(String sid, String text) {
    }

    /**
     * @param sidOfNew  새 문장마다 이어 받은 sid — {@code null} = 새 문장(새 주소)
     * @param edited    Dice 짝으로 이어져 판을 올릴 옛 sid
     * @param merged    합쳐진 옛 sid → 그 글을 품은 새 문장의 sid(사슬을 거쳐도 짝 문장을 바로 가리킨다)
     * @param splitFrom 나뉨 조각으로 기록할 새 문장 번호 → 출처 옛 sid
     * @param tombs     지워진 옛 sid
     */
    public record Alignment(List<String> sidOfNew, Set<String> edited, Map<String, String> merged,
                            Map<Integer, String> splitFrom, Set<String> tombs) {
    }

    private SentenceAligner() {
    }

    /** 한 문장을 한 번만 재 둔다 — 정규화 글 · 길이 · 바이그램 다중집합. */
    private record Measured(String norm, int len, Map<Long, Integer> bigrams, int size) {
        static Measured of(String text) {
            String norm = SentenceAligner.norm(text);
            int[] cps = norm.replace(" ", "").codePoints().toArray();
            Map<Long, Integer> bigrams = new HashMap<>();
            for (int i = 0; i + 1 < cps.length; i++) bigrams.merge(((long) cps[i] << 32) | cps[i + 1], 1, Integer::sum);
            return new Measured(norm, cps.length, bigrams, Math.max(cps.length - 1, 0));
        }
    }

    /** 공백 무리(§6.1 0번 술어)를 공백 하나로 · 앞뒤 걷기. */
    static String norm(String text) {
        StringBuilder sb = new StringBuilder();
        boolean gap = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SentenceSplitter.space(c)) {
                gap = true;
            } else {
                if (gap && !sb.isEmpty()) sb.append(' ');
                gap = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int inter(Measured a, Measured b) {
        int c = 0;
        for (Map.Entry<Long, Integer> e : a.bigrams().entrySet()) c += Math.min(e.getValue(), b.bigrams().getOrDefault(e.getKey(), 0));
        return c;
    }

    /** Dice를 분수 {@code {분자, 분모}}로 — 둘 다 빈 바이그램이면 정규화 글이 같을 때 1, 아니면 0. */
    private static long[] dice(Measured a, Measured b) {
        if (a.size() + b.size() == 0) return new long[]{a.norm().equals(b.norm()) ? 1 : 0, 1};
        return new long[]{2L * inter(a, b), a.size() + b.size()};
    }

    /** contain(part, whole) ≥ 0.6 ⇔ 5·|P∩W| ≥ 3·|P| — |P| = 0이면 거짓. */
    private static boolean contained(Measured part, Measured whole) {
        return part.size() > 0 && 5L * inter(part, whole) >= 3L * part.size();
    }

    public static Alignment align(List<Old> old, List<String> neu) {
        int n = old.size(), m = neu.size();
        Measured[] o = new Measured[n], w = new Measured[m];
        for (int a = 0; a < n; a++) o[a] = Measured.of(old.get(a).text());
        for (int b = 0; b < m; b++) w[b] = Measured.of(neu.get(b));
        int[] o2n = new int[n], n2o = new int[m];
        Arrays.fill(o2n, -1);
        Arrays.fill(n2o, -1);
        boolean[] edited = new boolean[n];

        // 1. LCS — 정규화 글이 같으면 짝(둘 다 한 칸), 아니면 dp[a+1][b] ≥ dp[a][b+1]일 때 옛 쪽을 건너뛴다
        int[][] dp = new int[n + 1][m + 1];
        for (int a = n - 1; a >= 0; a--)
            for (int b = m - 1; b >= 0; b--)
                dp[a][b] = o[a].norm().equals(w[b].norm()) ? dp[a + 1][b + 1] + 1 : Math.max(dp[a + 1][b], dp[a][b + 1]);
        for (int a = 0, b = 0; a < n && b < m; ) {
            if (o[a].norm().equals(w[b].norm())) {
                o2n[a] = b;
                n2o[b] = a;
                a++;
                b++;
            } else if (dp[a + 1][b] >= dp[a][b + 1]) a++;
            else b++;
        }

        // 2. 닻 사이 틈마다 Dice 짝 — 직전 짝보다 뒤 · 다음 닻보다 앞의 새 문장 가운데 가장 큰 것(같으면 앞의 것)
        List<int[]> anchors = new ArrayList<>();
        anchors.add(new int[]{-1, -1});
        for (int a = 0; a < n; a++) if (o2n[a] >= 0) anchors.add(new int[]{a, o2n[a]});
        anchors.add(new int[]{n, m});
        for (int g = 0; g + 1 < anchors.size(); g++) {
            int lastNew = anchors.get(g)[1], nextNew = anchors.get(g + 1)[1];
            for (int a = anchors.get(g)[0] + 1; a < anchors.get(g + 1)[0]; a++) {
                int best = -1;
                long bestNum = 0, bestDen = 1;
                for (int b = lastNew + 1; b < nextNew; b++) {
                    if (n2o[b] >= 0) continue;
                    long[] d = dice(o[a], w[b]);
                    if (2 * d[0] >= d[1] && (best < 0 || d[0] * bestDen > bestNum * d[1])) {
                        best = b;
                        bestNum = d[0];
                        bestDen = d[1];
                    }
                }
                if (best >= 0) {
                    o2n[a] = best;
                    n2o[best] = a;
                    edited[a] = true;
                    lastNew = best;
                }
            }
        }

        // 3. 합침(사슬) — 이웃이 짝이면 그 짝, 이미 어떤 대상으로 합쳐졌으면 그 대상. 자람은 누적으로 잰다
        int[] mergedTo = new int[n], grownBy = new int[n];
        Arrays.fill(mergedTo, -1);
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int a = 0; a < n; a++) {
                if (o2n[a] >= 0 || mergedTo[a] >= 0) continue;
                for (int c : new int[]{a - 1, a + 1}) {
                    if (c < 0 || c >= n) continue;
                    int t = o2n[c] >= 0 ? c : mergedTo[c];
                    if (t < 0) continue;
                    Measured grown = w[o2n[t]];
                    int grow = grown.len() - o[t].len() - grownBy[t];
                    if (contained(o[a], grown) && 2 * grow >= o[a].len()) {
                        mergedTo[a] = t;
                        grownBy[t] += o[a].len();
                        changed = true;
                        break;
                    }
                }
            }
        }

        // 4. 나뉨 기록(사슬 — 거울) — 이웃이 짝이면 그 옛 문장, 이미 어떤 출처의 조각이면 그 출처. 줄어듦은 누적으로 잰다
        int[] splitOf = new int[m], shrunkBy = new int[n];
        Arrays.fill(splitOf, -1);
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int b = 0; b < m; b++) {
                if (n2o[b] >= 0 || splitOf[b] >= 0) continue;
                for (int c : new int[]{b - 1, b + 1}) {
                    if (c < 0 || c >= m) continue;
                    int src = n2o[c] >= 0 ? n2o[c] : splitOf[c];
                    if (src < 0) continue;
                    int shrink = o[src].len() - w[o2n[src]].len() - shrunkBy[src];
                    if (contained(w[b], o[src]) && 2 * shrink >= w[b].len()) {
                        splitOf[b] = src;
                        shrunkBy[src] += w[b].len();
                        changed = true;
                        break;
                    }
                }
            }
        }

        // 5. 옮김 — 남은 옛 문장과 정규화 글이 같은 남은 새 문장
        for (int a = 0; a < n; a++) {
            if (o2n[a] >= 0 || mergedTo[a] >= 0) continue;
            for (int b = 0; b < m; b++) {
                if (n2o[b] >= 0 || splitOf[b] >= 0 || !o[a].norm().equals(w[b].norm())) continue;
                o2n[a] = b;
                n2o[b] = a;
                break;
            }
        }

        // 6. 결과
        String[] sidOfNew = new String[m];
        Map<Integer, String> splitFrom = new LinkedHashMap<>();
        for (int b = 0; b < m; b++) {
            if (n2o[b] >= 0) sidOfNew[b] = old.get(n2o[b]).sid();
            if (splitOf[b] >= 0) splitFrom.put(b, old.get(splitOf[b]).sid());
        }
        Set<String> editedSids = new LinkedHashSet<>(), tombs = new LinkedHashSet<>();
        Map<String, String> merged = new LinkedHashMap<>();
        for (int a = 0; a < n; a++) {
            String sid = old.get(a).sid();
            if (edited[a]) editedSids.add(sid);
            if (mergedTo[a] >= 0) merged.put(sid, old.get(mergedTo[a]).sid());
            else if (o2n[a] < 0) tombs.add(sid);
        }
        return new Alignment(Collections.unmodifiableList(Arrays.asList(sidOfNew)), Collections.unmodifiableSet(editedSids),
                Collections.unmodifiableMap(merged), Collections.unmodifiableMap(splitFrom), Collections.unmodifiableSet(tombs));
    }
}
