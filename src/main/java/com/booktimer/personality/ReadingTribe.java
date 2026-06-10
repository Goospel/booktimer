package com.booktimer.personality;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * 독서 종족 — 알라딘 대분류를 8종족의 닫힌 어휘로 매핑한다(책BTI Phase 6b 태그 생성).
 *
 * <p>같은 종족 어휘를 쓰는 사용자끼리 매칭할 수 있도록 어휘를 고정한다.
 * 미매핑 대분류는 {@link Optional#empty()}로 조용히 폴백(신규 카테고리가 깨지지 않음).
 */
public enum ReadingTribe {
    STORY("이야기파"),
    KNOWLEDGE("지식파"),
    INQUIRY("탐구파"),
    PRACTICAL("실용파"),
    SENTIMENT("감성파"),
    CULTIVATION("수양파"),
    STUDY("학습파"),
    CHILDHOOD("동심파");

    private final String label;

    ReadingTribe(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    private static final Map<String, ReadingTribe> CATEGORY_MAP = Map.ofEntries(
            // STORY
            Map.entry("소설/시/희곡",  STORY),
            Map.entry("만화",          STORY),
            Map.entry("라이트노벨",    STORY),
            // KNOWLEDGE
            Map.entry("인문학",        KNOWLEDGE),
            Map.entry("역사",          KNOWLEDGE),
            Map.entry("사회과학",      KNOWLEDGE),
            Map.entry("정치/사회",     KNOWLEDGE),
            // INQUIRY
            Map.entry("과학",          INQUIRY),
            Map.entry("자연과학",      INQUIRY),
            Map.entry("컴퓨터/모바일", INQUIRY),
            Map.entry("공학/기술",     INQUIRY),
            // PRACTICAL
            Map.entry("경제경영",      PRACTICAL),
            Map.entry("자기계발",      PRACTICAL),
            // SENTIMENT
            Map.entry("에세이",        SENTIMENT),
            Map.entry("여행",          SENTIMENT),
            Map.entry("예술/대중문화", SENTIMENT),
            // CULTIVATION
            Map.entry("종교/역학",     CULTIVATION),
            Map.entry("건강/취미",     CULTIVATION),
            Map.entry("가정/육아",     CULTIVATION),
            // STUDY
            Map.entry("외국어",        STUDY),
            Map.entry("수험서/자격증", STUDY),
            Map.entry("대학교재",      STUDY),
            Map.entry("사전",          STUDY),
            // CHILDHOOD
            Map.entry("유아",          CHILDHOOD),
            Map.entry("어린이",        CHILDHOOD),
            Map.entry("청소년",        CHILDHOOD)
    );

    /** 알라딘 대분류(2번째 세그먼트) → 종족. 미매핑이면 empty(). */
    public static Optional<ReadingTribe> ofCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CATEGORY_MAP.get(category.strip()));
    }

    /** 한글 라벨(예 "이야기파") → 종족. 라벨이 8종족 중 어느 것도 아니면 empty(). */
    public static Optional<ReadingTribe> ofLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String key = label.strip();
        return Arrays.stream(values()).filter(t -> t.label.equals(key)).findFirst();
    }
}
