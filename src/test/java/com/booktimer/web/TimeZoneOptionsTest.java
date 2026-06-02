package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TimeZoneOptions 단위 테스트 — 타임존 드롭다운에 채울 IANA zone ID 목록 계약.
 *
 * <p>사용자가 타임존 형식을 모르고도 고를 수 있게, 자유 입력 대신 select로 보여줄 후보를
 * 제공한다. 목록은 (1) 정렬되어 있고 (2) 모두 유효한 ZoneId이며 (3) 지역/도시 canonical
 * 이름("Asia/Seoul")만 담아 사람이 알아볼 수 있어야 한다.
 */
class TimeZoneOptionsTest {

    @Test
    @DisplayName("비어 있지 않고 흔한 지역을 포함한다")
    void all_containsCommonZones() {
        List<String> zones = TimeZoneOptions.all();

        assertThat(zones).isNotEmpty();
        assertThat(zones).contains("Asia/Seoul", "America/New_York", "Europe/London");
    }

    @Test
    @DisplayName("오름차순 정렬되어 있다")
    void all_isSorted() {
        List<String> zones = TimeZoneOptions.all();

        assertThat(zones).isSorted();
    }

    @Test
    @DisplayName("모든 항목이 유효한 ZoneId다")
    void all_everyEntryIsValidZoneId() {
        for (String id : TimeZoneOptions.all()) {
            assertThatCode(() -> ZoneId.of(id)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("지역/도시 canonical 이름만 — 짧은 레거시 별칭(EST 등)은 제외, 중복 없음")
    void all_onlyCanonicalRegionNames() {
        List<String> zones = TimeZoneOptions.all();

        assertThat(zones).allSatisfy(id -> assertThat(id).contains("/"));
        assertThat(zones).doesNotContain("EST", "GMT", "UTC", "SystemV/EST5");
        assertThat(zones).doesNotHaveDuplicates();
    }
}
