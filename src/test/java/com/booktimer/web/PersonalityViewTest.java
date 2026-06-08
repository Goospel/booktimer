package com.booktimer.web;

import com.booktimer.personality.LabeledCount;
import com.booktimer.personality.PersonalityHistoryEntry;
import com.booktimer.personality.PersonalityNarration;
import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 책BTI 화면 표시 모델 단위 테스트(Phase 5) — 분석 결과를 3가지 상태로 옳게 분류하는지 본다.
 *
 * <p>핵심: "서술 없음"의 이유가 콜드스타트(책 부족)인지 폴백(LLM 실패)인지 구분해야 화면 문구가 갈린다.
 */
class PersonalityViewTest {

    private static final int MIN = 5;

    private static ReadingProfile profileWith(int totalBooks) {
        return new ReadingProfile(totalBooks, totalBooks, 0, 0, 1.0, 0, 0, 0,
                0, List.of(), 0, List.of(), List.of());
    }

    private static ReadingProfile profileWith(int totalBooks, int finishedBooks) {
        return new ReadingProfile(totalBooks, finishedBooks, 0, 0, 1.0, 0, 0, 0,
                0, List.of(), 0, List.of(), List.of());
    }

    @Test
    @DisplayName("서술이 있으면 READY")
    void ready_whenNarrationPresent() {
        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("완독러다.", List.of("완독러")));

        PersonalityView view = PersonalityView.from(result, List.of(), MIN);

        assertThat(view.isReady()).isTrue();
        assertThat(view.narrative()).isEqualTo("완독러다.");
    }

    @Test
    @DisplayName("서술 없음 + 책 임계 미만이면 COLD_START")
    void coldStart_whenNoNarrationAndFewBooks() {
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(3)); // < 5

        PersonalityView view = PersonalityView.from(result, List.of(), MIN);

        assertThat(view.isColdStart()).isTrue();
        assertThat(view.narrative()).isNull();
    }

    @Test
    @DisplayName("서술 없음 + 책 충분하면 FALLBACK(LLM 실패)")
    void fallback_whenNoNarrationButEnoughBooks() {
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(7)); // >= 5

        PersonalityView view = PersonalityView.from(result, List.of(), MIN);

        assertThat(view.isFallback()).isTrue();
        assertThat(view.narrative()).isNull();
    }

    @Test
    @DisplayName("경계: 책이 정확히 임계면 콜드스타트 아님(임계 미만만 콜드스타트)")
    void boundary_exactlyThreshold_isNotColdStart() {
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(MIN)); // == 5

        PersonalityView view = PersonalityView.from(result, List.of(), MIN);

        assertThat(view.isColdStart()).isFalse();
        assertThat(view.isFallback()).isTrue();
    }

    @Test
    @DisplayName("콜드스타트는 완독 권수로 판정한다 — 보유는 많아도 완독이 임계 미만이면 콜드스타트")
    void coldStart_keysOnFinishedNotTotal() {
        // 보유 10권이지만 완독은 3권(<5) — 성향은 완독 책에서만 뽑으므로 아직 콜드스타트여야 한다
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(10, 3));

        PersonalityView view = PersonalityView.from(result, List.of(), MIN);

        assertThat(view.isColdStart()).isTrue();
    }

    @Test
    @DisplayName("READY일 때 히스토리(최대 3개)가 뷰에 그대로 실린다(최신순·대표 표시 보존)")
    void ready_carriesHistoryEntries() {
        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("대표 서술.", List.of("태그")));
        List<PersonalityHistoryEntry> entries = List.of(
                new PersonalityHistoryEntry(2L, "대표 서술.", List.of("태그"), Instant.parse("2026-06-08T01:00:00Z"), true, false),
                new PersonalityHistoryEntry(1L, "옛 서술.", List.of(), Instant.parse("2026-06-08T00:00:00Z"), false, true));

        PersonalityView view = PersonalityView.from(result, entries, MIN);

        assertThat(view.isReady()).isTrue();
        assertThat(view.entries()).hasSize(2);
        assertThat(view.entries().get(0).selected()).isTrue();
        assertThat(view.entries().get(1).stale()).isTrue();
    }
}
