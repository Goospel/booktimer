package com.booktimer.garden;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GardenIsoProjectionTest {

    private static final double TOL = 1e-9;

    // §2 표 — 5 샘플 불변식 (JS·Java 교차 일치 앵커)
    @Test void 중앙_0_5_0_5() {
        assertThat(GardenIsoProjection.screenX(0.5, 0.5)).isCloseTo(0.5,  within(TOL));
        assertThat(GardenIsoProjection.screenY(0.5, 0.5)).isCloseTo(0.5,  within(TOL));
    }

    @Test void 위_꼭짓점_0_0() {
        assertThat(GardenIsoProjection.screenX(0, 0)).isCloseTo(0.5,  within(TOL));
        assertThat(GardenIsoProjection.screenY(0, 0)).isCloseTo(0.25, within(TOL));
    }

    @Test void 오른쪽_꼭짓점_1_0() {
        assertThat(GardenIsoProjection.screenX(1, 0)).isCloseTo(1.0, within(TOL));
        assertThat(GardenIsoProjection.screenY(1, 0)).isCloseTo(0.5, within(TOL));
    }

    @Test void 아래_꼭짓점_1_1() {
        assertThat(GardenIsoProjection.screenX(1, 1)).isCloseTo(0.5,  within(TOL));
        assertThat(GardenIsoProjection.screenY(1, 1)).isCloseTo(0.75, within(TOL));
    }

    @Test void 왼쪽_꼭짓점_0_1() {
        assertThat(GardenIsoProjection.screenX(0, 1)).isCloseTo(0.0, within(TOL));
        assertThat(GardenIsoProjection.screenY(0, 1)).isCloseTo(0.5, within(TOL));
    }

    // 퍼센트 편의(SSR용) — 위 결과 ×100
    @Test void 퍼센트_중앙() {
        assertThat(GardenIsoProjection.screenXPercent(0.5, 0.5)).isCloseTo(50.0, within(TOL));
        assertThat(GardenIsoProjection.screenYPercent(0.5, 0.5)).isCloseTo(50.0, within(TOL));
    }

    @Test void 퍼센트_위() {
        assertThat(GardenIsoProjection.screenXPercent(0, 0)).isCloseTo(50.0, within(TOL));
        assertThat(GardenIsoProjection.screenYPercent(0, 0)).isCloseTo(25.0, within(TOL));
    }

    // PlacedItem 위임 메서드 — isoLeftPct / isoTopPct
    @Test void PlacedItem_위임_중앙() {
        PlacedItem item = new PlacedItem("plant", null, "code", "🌱", "name", null, 0.5, 0.5, 0, 0, 1);
        assertThat(item.isoLeftPct()).isCloseTo(50.0, within(TOL));
        assertThat(item.isoTopPct()).isCloseTo(50.0,  within(TOL));
    }

    @Test void PlacedItem_위임_위_꼭짓점() {
        PlacedItem item = new PlacedItem("plant", null, "code", "🌱", "name", null, 0, 0, 0, 0, 1);
        assertThat(item.isoLeftPct()).isCloseTo(50.0, within(TOL));
        assertThat(item.isoTopPct()).isCloseTo(25.0,  within(TOL));
    }
}
