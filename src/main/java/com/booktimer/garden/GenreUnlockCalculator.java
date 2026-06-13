package com.booktimer.garden;

import com.booktimer.personality.ReadingProfileAggregator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 장르 식물 보유 유도 — 순수 계산(DB·Spring·시간 무관).
 *
 * <p>시간축 {@link PlantUnlockCalculator}와 같은 철학: 보유를 저장하지 않고 <b>독서 실적의 함수로 유도</b>
 * 한다(부채 모델 N-058). 두 단계:
 * <ol>
 *   <li>{@link #achievedGenres} — 완독책 category들을 {@code primaryGenre}로 장르 대분류화해 보유 장르
 *       집합으로 모은다. category가 null/빈이거나 장르를 못 뽑으면 제외한다(미완성 메타 누수 가드 N-055).</li>
 *   <li>{@link #resolve} — 카탈로그 각 식물이 보유 장르에 잡히는지 판정한다. 폴백 식물(genreLabel=null)은
 *       보유 장르 중 어느 시드 라벨에도 안 맞는 게 하나라도 있으면 보유로 잡혀, "읽었는데 0개"인 허무를 막는다.</li>
 * </ol>
 */
public final class GenreUnlockCalculator {

    private GenreUnlockCalculator() {
    }

    /**
     * 완독책 category들에서 보유 장르(대분류) 집합을 뽑는다.
     *
     * @param finishedCategories 완독책의 category 목록(null 원소 허용 — 수동 등록 책은 category가 null)
     * @return 보유 장르 라벨 집합(입력 순서 유지). 장르를 못 뽑은 책은 제외(N-055).
     */
    public static Set<String> achievedGenres(List<String> finishedCategories) {
        Set<String> genres = new LinkedHashSet<>();
        for (String category : finishedCategories) {
            String genre = ReadingProfileAggregator.primaryGenre(category);
            if (genre != null) {
                genres.add(genre);
            }
        }
        return genres;
    }

    /**
     * 카탈로그 각 식물의 보유 여부를 판정해 도감 칸으로 돌려준다(카탈로그 순서 유지).
     *
     * @param catalog     장르 식물 카탈로그(진열 순서). 폴백 식물({@code isFallback()})을 끝에 포함할 수 있다.
     * @param ownedGenres 보유 장르 집합({@link #achievedGenres} 출력)
     * @return 식물별 보유 상태. 시드 식물은 라벨 일치면 보유, 폴백은 시드에 없는 장르가 있으면 보유.
     */
    public static List<GenrePlantState> resolve(List<GenrePlant> catalog, Set<String> ownedGenres) {
        // 폴백 발동 = 보유 장르 중 어느 시드 라벨에도 안 맞는 게 하나라도 있는가.
        Set<String> seedLabels = catalog.stream()
                .map(GenrePlant::getGenreLabel)
                .filter(label -> label != null)
                .collect(java.util.stream.Collectors.toSet());
        boolean fallbackOwned = ownedGenres.stream().anyMatch(g -> !seedLabels.contains(g));

        return catalog.stream()
                .map(plant -> {
                    boolean owned = plant.isFallback()
                            ? fallbackOwned
                            : ownedGenres.contains(plant.getGenreLabel());
                    return new GenrePlantState(plant, owned);
                })
                .toList();
    }
}
