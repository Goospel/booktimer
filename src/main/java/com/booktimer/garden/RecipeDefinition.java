package com.booktimer.garden;

import java.util.List;
import java.util.function.Predicate;

/**
 * 트랙 B 숨은 레시피 한 건 — 결과 식물 코드({@code plantCode})와 그 식물을 내는 조건({@code condition}).
 *
 * <p>조건은 임의 술어({@code Predicate<ShelfSnapshot>})라 SQL 컬럼화가 부자연스럽다(완독 장르X AND 읽고싶음
 * 작가Y …). 그래서 <b>레시피 정의는 코드 상수</b>로 두고, DB({@code recipe_plant})에는 결과 식물의 표시 메타
 * (이름·이모지·스토리)만 시드한다 — 둘은 {@code plantCode}로 연결된다(설계 §2.3).
 *
 * <p>{@link #ALL}은 <b>베타 예시 큐레이션</b>이다. 메커닉(완독+읽고싶음 조합 / 숨김 발견 / 발견 저장·영구 보유
 * / 완독 ≥1 파밍 방지)을 작동시키는 예시일 뿐 확정 큐레이션이 아니며, 식물 카탈로그를 직접 커스텀(관리자 입력)
 * 으로 관리하는 단계에서 교체된다(계획 §8, 사용자 결정 2026-06-13). 모든 레시피는 완독 책을 요구한다 —
 * 읽고싶음만으론 해금되지 않게 파밍을 막는다(읽고싶음은 방향/취향 양념)(§2.4).
 *
 * <p>장르 라벨은 {@code ReadingProfileAggregator.primaryGenre} 출력·트랙 A 시드(V36)와 같은 표기를 쓴다.
 */
public record RecipeDefinition(String plantCode, Predicate<ShelfSnapshot> condition) {

    /** 베타 예시 레시피 8종 — 커스텀 카탈로그 단계에서 교체될 placeholder(계획 §8). */
    public static final List<RecipeDefinition> ALL = List.of(
            // 🪷 연꽃 — 감성으로 읽고 이성을 탐낸다
            new RecipeDefinition("lotus",
                    s -> s.finishedGenres().contains("소설/시/희곡") && s.wantToReadGenres().contains("과학")),
            // 🥝 호기심 열매 — 원리를 알고 의미를 묻는다
            new RecipeDefinition("curiosity",
                    s -> s.finishedGenres().contains("과학") && s.wantToReadGenres().contains("인문학")),
            // 🍅 노을 토마토 — 이야기에서 아름다움으로
            new RecipeDefinition("sunset",
                    s -> s.finishedGenres().contains("소설/시/희곡") && s.wantToReadGenres().contains("예술/대중문화")),
            // 🍈 도전 멜론 — 나를 갈고 세상에 나선다
            new RecipeDefinition("bluebell",
                    s -> s.finishedGenres().contains("자기계발") && s.wantToReadGenres().contains("경제경영")),
            // 🥥 탐험가 야자열매 — 편식 없는 탐험가 (완독 서로 다른 장르 3종 이상)
            new RecipeDefinition("explorer",
                    s -> s.finishedGenres().size() >= 3),
            // 🌽 황금 옥수수 — 꾸준한 수확과 다음 씨앗 (완독 5권 이상 + 읽고싶음 3권 이상)
            new RecipeDefinition("harvest",
                    s -> s.finishedCount() >= 5 && s.wantToReadCount() >= 3),
            // 🥕 단골 당근 — 한 작가를 깊이 따라간다 (같은 작가 완독 2권 이상; 메타 신뢰성 리스크 큼 §5.2)
            new RecipeDefinition("regular",
                    s -> s.finishedAuthorCounts().values().stream().anyMatch(count -> count >= 2)),
            // 🫒 시간의 올리브 — 지나간 시간을 더 깊이 판다 (완독 역사 + 읽고싶음 역사)
            new RecipeDefinition("timekeeper",
                    s -> s.finishedGenres().contains("역사") && s.wantToReadGenres().contains("역사")));
}
