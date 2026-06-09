package com.booktimer.personality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 입력 시그니처 단위 테스트(책BTI Phase 4) — 재생성 트리거의 정확도를 본다.
 *
 * <p>핵심: (1) 같은 프로필이면 같은 시그니처(결정적·캐시 키), (2) 완독 책의 <b>구조</b>(권수·저자·장르)가
 * 바뀌면 달라진다(재생성), (3) <b>독서 시간은 더 이상 성향 입력이 아니므로 시그니처를 바꾸지 않는다</b> —
 * 책을 더 완독하지 않고 시간만 쌓여도 재분석하지 않는다(완독 책만으로 성향, 시간 제외).
 */
class ProfileSignatureTest {

    private static ReadingProfile profile(int totalBooks, long totalSeconds, List<LabeledCount> genres) {
        return new ReadingProfile(totalBooks, totalBooks, 0, 0, 1.0, totalSeconds, 1, totalSeconds,
                1, List.of(new LabeledCount("김영하", totalBooks)),
                genres.size(), genres);
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
    @DisplayName("독서 시간은 시그니처에 영향 없음: 시간만 늘어도(시간 버킷을 넘겨도) 시그니처는 그대로 — 시간은 성향 입력이 아님")
    void readingTimeChange_doesNotChangeSignature() {
        List<LabeledCount> genres = List.of(new LabeledCount("소설/시/희곡", 3));
        String oneHour = ProfileSignature.of(profile(3, 3600, genres));    // 1h
        String fiveHours = ProfileSignature.of(profile(3, 18000, genres)); // 5h (예전이라면 버킷을 넘겨 달랐을 값)
        assertThat(fiveHours).isEqualTo(oneHour); // 시간이 달라도 같은 시그니처(재분석 안 함)
    }
}
