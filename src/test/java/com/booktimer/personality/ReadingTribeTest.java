package com.booktimer.personality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingTribeTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("대분류 → 종족 매핑")
    @CsvSource({
            "소설/시/희곡,   STORY",
            "만화,           STORY",
            "라이트노벨,     STORY",
            "인문학,         KNOWLEDGE",
            "역사,           KNOWLEDGE",
            "사회과학,       KNOWLEDGE",
            "정치/사회,      KNOWLEDGE",
            "과학,           INQUIRY",
            "자연과학,       INQUIRY",
            "컴퓨터/모바일,  INQUIRY",
            "공학/기술,      INQUIRY",
            "경제경영,       PRACTICAL",
            "자기계발,       PRACTICAL",
            "에세이,         SENTIMENT",
            "여행,           SENTIMENT",
            "예술/대중문화,  SENTIMENT",
            "종교/역학,      CULTIVATION",
            "건강/취미,      CULTIVATION",
            "가정/육아,      CULTIVATION",
            "외국어,         STUDY",
            "수험서/자격증,  STUDY",
            "대학교재,       STUDY",
            "사전,           STUDY",
            "유아,           CHILDHOOD",
            "어린이,         CHILDHOOD",
            "청소년,         CHILDHOOD",
    })
    void mappedCategories(String category, String expectedTribe) {
        assertThat(ReadingTribe.ofCategory(category.strip()))
                .isPresent()
                .contains(ReadingTribe.valueOf(expectedTribe.strip()));
    }

    @Test
    @DisplayName("미매핑 대분류 → Optional.empty()(폴백 — 깨지지 않음)")
    void unmappedCategory_returnsEmpty() {
        assertThat(ReadingTribe.ofCategory("알 수 없는 분류")).isEmpty();
        assertThat(ReadingTribe.ofCategory("기타")).isEmpty();
    }

    @Test
    @DisplayName("null/blank → Optional.empty()")
    void nullOrBlank_returnsEmpty() {
        assertThat(ReadingTribe.ofCategory(null)).isEmpty();
        assertThat(ReadingTribe.ofCategory("")).isEmpty();
        assertThat(ReadingTribe.ofCategory("  ")).isEmpty();
    }

    @Test
    @DisplayName("label() 게터가 한글 표시명을 돌려준다")
    void labelReturnsKoreanName() {
        assertThat(ReadingTribe.STORY.label()).isEqualTo("이야기파");
        assertThat(ReadingTribe.KNOWLEDGE.label()).isEqualTo("지식파");
        assertThat(ReadingTribe.INQUIRY.label()).isEqualTo("탐구파");
        assertThat(ReadingTribe.PRACTICAL.label()).isEqualTo("실용파");
        assertThat(ReadingTribe.SENTIMENT.label()).isEqualTo("감성파");
        assertThat(ReadingTribe.CULTIVATION.label()).isEqualTo("수양파");
        assertThat(ReadingTribe.STUDY.label()).isEqualTo("학습파");
        assertThat(ReadingTribe.CHILDHOOD.label()).isEqualTo("동심파");
    }
}
