package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AffectionLevel 순수 단위 테스트 — feedCount → level·title·nextThreshold 임계 전수.
 *
 * <p>임계는 AffectionLevel 단일 출처 — 이 파일에서만 칭호 문자열을 단언한다.
 */
class AffectionLevelTest {

    @Test
    @DisplayName("feedCount 0 → level 0, title 비어있음, nextThreshold 1")
    void level_0_feedCount0() {
        AffectionLevel al = AffectionLevel.of(0);
        assertThat(al.level()).isEqualTo(0);
        assertThat(al.title()).isEmpty();
        assertThat(al.nextThreshold()).isEqualTo(1);
    }

    @Test
    @DisplayName("feedCount 1 → level 1, 안면 텄어요, nextThreshold 3")
    void level_1_feedCount1() {
        AffectionLevel al = AffectionLevel.of(1);
        assertThat(al.level()).isEqualTo(1);
        assertThat(al.title()).isEqualTo("안면 텄어요");
        assertThat(al.nextThreshold()).isEqualTo(3);
    }

    @Test
    @DisplayName("feedCount 2 → level 1 (임계 3 직전), nextThreshold 3")
    void level_1_feedCount2_boundary() {
        AffectionLevel al = AffectionLevel.of(2);
        assertThat(al.level()).isEqualTo(1);
        assertThat(al.nextThreshold()).isEqualTo(3);
    }

    @Test
    @DisplayName("feedCount 3 → level 2, 친해지는 중, nextThreshold 7")
    void level_2_feedCount3() {
        AffectionLevel al = AffectionLevel.of(3);
        assertThat(al.level()).isEqualTo(2);
        assertThat(al.title()).isEqualTo("친해지는 중");
        assertThat(al.nextThreshold()).isEqualTo(7);
    }

    @Test
    @DisplayName("feedCount 6 → level 2 (임계 7 직전), nextThreshold 7")
    void level_2_feedCount6_boundary() {
        AffectionLevel al = AffectionLevel.of(6);
        assertThat(al.level()).isEqualTo(2);
        assertThat(al.nextThreshold()).isEqualTo(7);
    }

    @Test
    @DisplayName("feedCount 7 → level 3, 친한 사이, nextThreshold 15")
    void level_3_feedCount7() {
        AffectionLevel al = AffectionLevel.of(7);
        assertThat(al.level()).isEqualTo(3);
        assertThat(al.title()).isEqualTo("친한 사이");
        assertThat(al.nextThreshold()).isEqualTo(15);
    }

    @Test
    @DisplayName("feedCount 14 → level 3 (임계 15 직전), nextThreshold 15")
    void level_3_feedCount14_boundary() {
        AffectionLevel al = AffectionLevel.of(14);
        assertThat(al.level()).isEqualTo(3);
        assertThat(al.nextThreshold()).isEqualTo(15);
    }

    @Test
    @DisplayName("feedCount 15 → level 4, 단짝, nextThreshold 30")
    void level_4_feedCount15() {
        AffectionLevel al = AffectionLevel.of(15);
        assertThat(al.level()).isEqualTo(4);
        assertThat(al.title()).isEqualTo("단짝");
        assertThat(al.nextThreshold()).isEqualTo(30);
    }

    @Test
    @DisplayName("feedCount 29 → level 4 (임계 30 직전), nextThreshold 30")
    void level_4_feedCount29_boundary() {
        AffectionLevel al = AffectionLevel.of(29);
        assertThat(al.level()).isEqualTo(4);
        assertThat(al.nextThreshold()).isEqualTo(30);
    }

    @Test
    @DisplayName("feedCount 30 → level 5, 베프, nextThreshold null (최대)")
    void level_5_feedCount30() {
        AffectionLevel al = AffectionLevel.of(30);
        assertThat(al.level()).isEqualTo(5);
        assertThat(al.title()).isEqualTo("베프");
        assertThat(al.nextThreshold()).isNull();
    }

    @Test
    @DisplayName("feedCount 100 → level 5 유지, nextThreshold null")
    void level_5_feedCountBig() {
        AffectionLevel al = AffectionLevel.of(100);
        assertThat(al.level()).isEqualTo(5);
        assertThat(al.nextThreshold()).isNull();
    }
}
