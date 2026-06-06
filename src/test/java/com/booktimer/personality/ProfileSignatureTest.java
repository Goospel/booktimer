package com.booktimer.personality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 입력 시그니처 단위 테스트(책BTI Phase 4) — 재생성 트리거의 정확도를 본다.
 *
 * <p>핵심: (1) 같은 프로필이면 같은 시그니처(결정적·캐시 키), (2) 책장 <b>구조</b>가 바뀌면 달라진다(재생성),
 * (3) 독서 시간이 <b>같은 시간(hour) 버킷 안</b>에서만 바뀌면 그대로다(측정 세션마다 재생성 thrash 회피).
 */
class ProfileSignatureTest {

    private static ReadingProfile profile(int totalBooks, long totalSeconds, List<LabeledCount> genres) {
        return new ReadingProfile(totalBooks, totalBooks, 0, 0, 1.0, totalSeconds, 1, totalSeconds,
                1, List.of(new LabeledCount("김영하", totalBooks)),
                genres.size(), genres,
                List.of(new LabeledCount("2020", totalBooks)));
    }

    @Test
    @DisplayName("결정적: 같은 프로필이면 같은 시그니처")
    void deterministic_sameProfileSameSignature() {
        List<LabeledCount> genres = List.of(new LabeledCount("소설/시/희곡", 3));
        assertThat(ProfileSignature.of(profile(3, 3600, genres)))
                .isNotBlank()
                .isEqualTo(ProfileSignature.of(profile(3, 3600, genres)));
    }

    @Test
    @DisplayName("구조 변화: 권수가 늘면 시그니처가 달라진다(재생성 트리거)")
    void structuralChange_changesSignature() {
        List<LabeledCount> genres = List.of(new LabeledCount("소설/시/희곡", 3));
        String before = ProfileSignature.of(profile(3, 3600, genres));
        String after = ProfileSignature.of(profile(4, 3600, genres));
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("구조 변화: 장르 분포가 바뀌면 시그니처가 달라진다")
    void genreChange_changesSignature() {
        String fiction = ProfileSignature.of(profile(3, 3600, List.of(new LabeledCount("소설/시/희곡", 3))));
        String econ = ProfileSignature.of(profile(3, 3600, List.of(new LabeledCount("경제경영", 3))));
        assertThat(econ).isNotEqualTo(fiction);
    }

    @Test
    @DisplayName("시간 버킷: 같은 시간(hour) 안의 초 변화는 시그니처를 안 바꾼다(thrash 회피)")
    void withinSameHour_sameSignature() {
        List<LabeledCount> genres = List.of(new LabeledCount("소설/시/희곡", 3));
        // 3600초(1h)와 7000초(여전히 1h 버킷)는 같은 시그니처
        assertThat(ProfileSignature.of(profile(3, 7000, genres)))
                .isEqualTo(ProfileSignature.of(profile(3, 3600, genres)));
    }

    @Test
    @DisplayName("시간 버킷: 시간(hour) 버킷을 넘으면 시그니처가 달라진다(의미있는 누적)")
    void crossingHourBucket_changesSignature() {
        List<LabeledCount> genres = List.of(new LabeledCount("소설/시/희곡", 3));
        String oneHour = ProfileSignature.of(profile(3, 3600, genres));   // 1h
        String twoHours = ProfileSignature.of(profile(3, 7200, genres));  // 2h
        assertThat(twoHours).isNotEqualTo(oneHour);
    }
}
