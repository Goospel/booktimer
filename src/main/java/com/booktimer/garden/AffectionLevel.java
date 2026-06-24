package com.booktimer.garden;

/**
 * 정(affection) 레벨 — feedCount를 5단계로 끊어 칭호·다음 임계를 유도한다.
 *
 * <p>레벨은 저장하지 않고 feedCount에서 매번 유도(N-058 count-stores-level-derives 원칙).
 * 임계 테이블의 단일 출처: 이 클래스만 임계·칭호를 정의한다 — 프론트는 백엔드 응답을 그대로 쓴다.
 *
 * @param level         0–5 정수. 0 = 첫 먹이기 전.
 * @param title         단계 칭호. level 0이면 빈 문자열.
 * @param nextThreshold 다음 레벨 진입에 필요한 누적 feedCount. 최대 레벨이면 {@code null}.
 */
public record AffectionLevel(int level, String title, Integer nextThreshold) {

    /**
     * feedCount → AffectionLevel 유도.
     *
     * <p>임계 테이블 (초반 빠른 성취 — thesis 친화):
     * <pre>
     *   feedCount  level  title       nextThreshold
     *   0          0      ""          1
     *   1–2        1      안면 텄어요   3
     *   3–6        2      친해지는 중  7
     *   7–14       3      친한 사이    15
     *   15–29      4      단짝         30
     *   30+        5      베프         null
     * </pre>
     */
    public static AffectionLevel of(int feedCount) {
        if (feedCount <= 0)  return new AffectionLevel(0, "", 1);
        if (feedCount < 3)   return new AffectionLevel(1, "안면 텄어요", 3);
        if (feedCount < 7)   return new AffectionLevel(2, "친해지는 중", 7);
        if (feedCount < 15)  return new AffectionLevel(3, "친한 사이", 15);
        if (feedCount < 30)  return new AffectionLevel(4, "단짝", 30);
        return new AffectionLevel(5, "베프", null);
    }
}
