package com.booktimer.web;

import com.booktimer.personality.LabeledCount;
import com.booktimer.personality.PersonalityHistoryEntry;
import com.booktimer.personality.PersonalityNarration;
import com.booktimer.personality.ReadingPersonality;
import com.booktimer.personality.ReadingProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 책BTI 화면 표시 모델 단위 테스트(Phase 5) — 분석 결과를 3가지 상태로 옳게 분류하는지 본다.
 *
 * <p>핵심: "서술 없음"의 이유가 콜드스타트(책 부족)인지 폴백(LLM 실패)인지 구분해야 화면 문구가 갈린다.
 */
class PersonalityViewTest {

    private static final int MIN = 5;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static ReadingProfile profileWith(int totalBooks) {
        return new ReadingProfile(totalBooks, totalBooks, 0, 0, 1.0, 0, 0, 0,
                0, List.of(), 0, List.of());
    }

    private static ReadingProfile profileWith(int totalBooks, int finishedBooks) {
        return new ReadingProfile(totalBooks, finishedBooks, 0, 0, 1.0, 0, 0, 0,
                0, List.of(), 0, List.of());
    }

    @Test
    @DisplayName("서술이 있으면 READY")
    void ready_whenNarrationPresent() {
        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("완독러다.", List.of("완독러")));

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.isReady()).isTrue();
        assertThat(view.narrative()).isEqualTo("완독러다.");
    }

    @Test
    @DisplayName("서술 없음 + 책 임계 미만이면 COLD_START")
    void coldStart_whenNoNarrationAndFewBooks() {
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(3)); // < 5

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.isColdStart()).isTrue();
        assertThat(view.narrative()).isNull();
    }

    @Test
    @DisplayName("서술 없음 + 책 충분하면 FALLBACK(LLM 실패)")
    void fallback_whenNoNarrationButEnoughBooks() {
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(7)); // >= 5

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.isFallback()).isTrue();
        assertThat(view.narrative()).isNull();
    }

    @Test
    @DisplayName("경계: 책이 정확히 임계면 콜드스타트 아님(임계 미만만 콜드스타트)")
    void boundary_exactlyThreshold_isNotColdStart() {
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(MIN)); // == 5

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.isColdStart()).isFalse();
        assertThat(view.isFallback()).isTrue();
    }

    @Test
    @DisplayName("콜드스타트는 완독 권수로 판정한다 — 보유는 많아도 완독이 임계 미만이면 콜드스타트")
    void coldStart_keysOnFinishedNotTotal() {
        // 보유 10권이지만 완독은 3권(<5) — 성향은 완독 책에서만 뽑으므로 아직 콜드스타트여야 한다
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWith(10, 3));

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

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

        PersonalityView view = PersonalityView.from(result, entries, MIN, KST);

        assertThat(view.isReady()).isTrue();
        assertThat(view.entries()).hasSize(2);
        assertThat(view.entries().get(0).selected()).isTrue();
        assertThat(view.entries().get(1).stale()).isTrue();
    }

    @Test
    @DisplayName("분석 시각을 사용자 타임존으로 포맷한다(UTC 저장값 → KST 표시) — 서버 UTC로 9시간 어긋나던 버그 방지")
    void formatTime_rendersInUserZone() {
        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("서술.", List.of("태그")));
        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        // 2026-06-08T08:43:00Z == 2026-06-08 17:43 KST (UTC+9). 서버 UTC로 찍으면 08:43으로 잘못 보였다.
        assertThat(view.formatTime(Instant.parse("2026-06-08T08:43:00Z")))
                .isEqualTo("2026-06-08 17:43");
    }

    @Test
    @DisplayName("formatTime은 null 시각에 빈 문자열을 돌려준다(방어)")
    void formatTime_nullSafe() {
        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("서술.", List.of("태그")));
        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.formatTime(null)).isEmpty();
    }

    // ---- displayEntries() 정렬 테스트 ----

    @Test
    @DisplayName("displayEntries — 대표가 최신이 아닐 때 대표를 첫 칸으로, 나머지는 최신순")
    void displayEntries_putsRepresentativeFirstThenNewest() {
        // entries()는 최신순 계약(service가 그렇게 줌)
        // 대표(rep_old)가 가장 오래된 경우 — entries() 순서는 cand_new > cand_mid > rep_old
        PersonalityHistoryEntry candNew = new PersonalityHistoryEntry(3L, "새 후보.", List.of(),
                Instant.parse("2026-06-09T03:00:00Z"), false, false);
        PersonalityHistoryEntry candMid = new PersonalityHistoryEntry(2L, "중간 후보.", List.of(),
                Instant.parse("2026-06-09T02:00:00Z"), false, false);
        PersonalityHistoryEntry repOld = new PersonalityHistoryEntry(1L, "옛 대표.", List.of(),
                Instant.parse("2026-06-09T01:00:00Z"), true, true);
        List<PersonalityHistoryEntry> entries = List.of(candNew, candMid, repOld);

        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("옛 대표.", List.of()));
        PersonalityView view = PersonalityView.from(result, entries, MIN, KST);

        // 대표가 먼저, 나머지는 최신순
        assertThat(view.displayEntries()).containsExactly(repOld, candNew, candMid);
        // entries()는 최신순 원본 유지(회귀 가드)
        assertThat(view.entries()).containsExactly(candNew, candMid, repOld);
    }

    @Test
    @DisplayName("displayEntries — 카드 1개면 그 1개만(스와이프 없음 경계)")
    void displayEntries_singleEntry() {
        PersonalityHistoryEntry single = new PersonalityHistoryEntry(1L, "서술.", List.of(),
                Instant.parse("2026-06-09T01:00:00Z"), true, false);
        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("서술.", List.of()));
        PersonalityView view = PersonalityView.from(result, List.of(single), MIN, KST);

        assertThat(view.displayEntries()).containsExactly(single);
    }

    // ---- 태그 소스: LLM 아닌 ReadingTagger (Increment 3) ----

    /** 완독 7권, 소설/시/희곡 7권 → ReadingTagger가 ["이야기파", "외길형"] 산출 예상 */
    private static ReadingProfile profileWithStoryGenre(int finishedBooks) {
        return new ReadingProfile(finishedBooks, finishedBooks, 0, 0, 1.0, 0, 0, 0,
                0, List.of(), 1, List.of(new LabeledCount("소설/시/희곡", finishedBooks)));
    }

    @Test
    @DisplayName("READY 상태라도 tags는 LLM 태그가 아닌 ReadingTagger에서 온다")
    void ready_tagsFromTagger_notLlm() {
        // LLM 태그와 다른 값을 일부러 설정 → 뷰가 LLM 태그를 쓰면 이 단언 실패
        ReadingPersonality result = new ReadingPersonality(profileWithStoryGenre(7),
                new PersonalityNarration("서술.", List.of("LLM태그_무시해야함")));

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.tags()).doesNotContain("LLM태그_무시해야함");
        assertThat(view.tags()).contains("이야기파", "외길형");
    }

    @Test
    @DisplayName("FALLBACK 상태에서도 tags가 ReadingTagger에서 온다")
    void fallback_tagsFromTagger() {
        ReadingPersonality result = ReadingPersonality.factsOnly(profileWithStoryGenre(7));

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.isFallback()).isTrue();
        assertThat(view.tags()).contains("이야기파");
    }

    @Test
    @DisplayName("COLD_START(완독 0권)는 태그 비어있다")
    void coldStart_tagsEmpty() {
        ReadingPersonality result = ReadingPersonality.factsOnly(
                new ReadingProfile(0, 0, 0, 0, 0.0, 0, 0, 0, 0, List.of(), 0, List.of()));

        PersonalityView view = PersonalityView.from(result, List.of(), MIN, KST);

        assertThat(view.isColdStart()).isTrue();
        assertThat(view.tags()).isEmpty();
    }

    @Test
    @DisplayName("displayEntries — selected 0개(방어)면 최신순 그대로")
    void displayEntries_noSelected_keepsNewest() {
        PersonalityHistoryEntry e1 = new PersonalityHistoryEntry(2L, "새 서술.", List.of(),
                Instant.parse("2026-06-09T02:00:00Z"), false, false);
        PersonalityHistoryEntry e2 = new PersonalityHistoryEntry(1L, "옛 서술.", List.of(),
                Instant.parse("2026-06-09T01:00:00Z"), false, false);
        ReadingPersonality result = new ReadingPersonality(profileWith(10),
                new PersonalityNarration("새 서술.", List.of()));
        PersonalityView view = PersonalityView.from(result, List.of(e1, e2), MIN, KST);

        assertThat(view.displayEntries()).containsExactly(e1, e2);
    }
}
