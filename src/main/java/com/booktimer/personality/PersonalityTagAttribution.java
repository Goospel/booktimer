package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 책BTI 태그 → 근거 책 귀속(순수) — Phase 6b 3단계.
 *
 * <p>입력은 호출자(서비스)가 미리 거른 <b>공개 책 목록</b>(상태 무관). 출력은 그 중 완독이면서
 * 주어진 태그를 만든 책만. 비공개 책은 애초에 들어오지 않는다(누출 방지는 서비스 계층의 책임).
 *
 * <p>태그 종류:
 * <ul>
 *   <li>장르 종족 라벨({@link ReadingTribe#ofLabel}) → 그 종족 대분류({@link ReadingProfileAggregator#primaryGenre}) 책</li>
 *   <li>{@link ReadingTagger#AUTHOR_LOYAL_TAG} → 최다독 저자 책(동률 시 저자명 오름차순으로 단일 선택)</li>
 *   <li>그 외(폭 라벨·오타·null·blank) → 빈 목록</li>
 * </ul>
 */
public final class PersonalityTagAttribution {

    private PersonalityTagAttribution() {}

    public static List<Book> booksForTag(List<Book> publicBooks, String tag) {
        if (publicBooks == null || publicBooks.isEmpty() || tag == null || tag.isBlank()) {
            return List.of();
        }
        String t = tag.strip();

        Optional<ReadingTribe> tribe = ReadingTribe.ofLabel(t);
        if (tribe.isPresent()) {
            return finishedByTribe(publicBooks, tribe.get());
        }
        if (ReadingTagger.AUTHOR_LOYAL_TAG.equals(t)) {
            return finishedByTopAuthor(publicBooks);
        }
        return List.of();
    }

    private static List<Book> finishedByTribe(List<Book> books, ReadingTribe tribe) {
        return books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .filter(b -> ReadingTribe.ofCategory(ReadingProfileAggregator.primaryGenre(b.getCategory()))
                        .map(t -> t == tribe).orElse(false))
                .toList();
    }

    private static List<Book> finishedByTopAuthor(List<Book> books) {
        Map<String, Integer> counts = new HashMap<>();
        for (Book b : books) {
            if (b.getStatus() != BookStatus.FINISHED) continue;
            String author = WriterName.lead(b.getAuthor()); // 글쓴이만(옮긴이·그림 등 비저자 역할 제외)
            if (author == null) continue;
            counts.merge(author, 1, Integer::sum);
        }
        if (counts.isEmpty()) return List.of();

        String topAuthor = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        return books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .filter(b -> topAuthor.equals(WriterName.lead(b.getAuthor())))
                .toList();
    }
}
