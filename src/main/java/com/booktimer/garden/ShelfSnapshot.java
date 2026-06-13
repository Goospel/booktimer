package com.booktimer.garden;

import com.booktimer.personality.ReadingProfileAggregator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 트랙 B 레시피 평가의 순수 입력 — 책장(완독·읽고싶음)을 레시피가 묻는 차원으로 미리 집계한 스냅샷.
 *
 * <p>DB·Spring·시간 무관한 순수 값이라 {@link RecipeEvaluator}를 전수 단위테스트할 수 있다. 원시 책 메타
 * ({@link BookMeta} = 작가·카테고리)에서 다음을 뽑는다:
 * <ul>
 *   <li>{@code finishedGenres}/{@code wantToReadGenres} — 카테고리를 {@code primaryGenre}로 장르 대분류화한
 *       집합(중복 흡수). 장르를 못 뽑은(category null/빈) 책은 제외한다 — 미완성 메타 누수 가드(N-055).</li>
 *   <li>{@code finishedCount}/{@code wantToReadCount} — 권수(카운트형 레시피용). 장르 유무와 무관한 전체 권수.</li>
 *   <li>{@code finishedAuthorCounts} — 완독책의 작가별 권수(작가축 레시피용). author null/빈은 제외(N-055).</li>
 * </ul>
 *
 * <p>장르 라벨은 {@code ReadingProfileAggregator.primaryGenre} 출력(알라딘 카테고리 2번째 세그먼트)과 같은
 * 표기를 쓴다 — 트랙 A 장르 식물(V36)·레시피 정의가 한 라벨 체계를 공유해야 매칭이 어긋나지 않는다.
 */
public record ShelfSnapshot(Set<String> finishedGenres,
                            Set<String> wantToReadGenres,
                            int finishedCount,
                            int wantToReadCount,
                            Map<String, Long> finishedAuthorCounts) {

    /** 레시피 평가에 필요한 책 한 권의 최소 메타(작가·카테고리). 둘 다 null 가능(수동 등록 책). */
    public record BookMeta(String author, String category) {
    }

    /**
     * 완독·읽고싶음 책 메타에서 스냅샷을 집계한다. 장르·작가는 null/빈을 제외하고(N-055), 권수는 전체를 센다.
     */
    public static ShelfSnapshot of(List<BookMeta> finished, List<BookMeta> wantToRead) {
        return new ShelfSnapshot(
                genresOf(finished),
                genresOf(wantToRead),
                finished.size(),
                wantToRead.size(),
                authorCountsOf(finished));
    }

    /** 책 카테고리들을 장르 대분류 집합으로 — 장르를 못 뽑은 책은 제외(입력 순서 유지). */
    private static Set<String> genresOf(List<BookMeta> books) {
        Set<String> genres = new LinkedHashSet<>();
        for (BookMeta book : books) {
            String genre = ReadingProfileAggregator.primaryGenre(book.category());
            if (genre != null) {
                genres.add(genre);
            }
        }
        return genres;
    }

    /** 완독책의 작가별 권수 — author가 null/빈인 책은 제외(같은 작가로 묶이지 않게)(입력 순서 유지). */
    private static Map<String, Long> authorCountsOf(List<BookMeta> books) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BookMeta book : books) {
            String author = book.author();
            if (author != null && !author.isBlank()) {
                counts.merge(author.strip(), 1L, Long::sum);
            }
        }
        return counts;
    }
}
