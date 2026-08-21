package com.booktimer.book;

import com.booktimer.personality.LabeledCount;
import com.booktimer.personality.ReadingProfile;
import com.booktimer.personality.ReadingProfileAggregator;
import com.booktimer.personality.WriterName;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 「책 추가」 화면의 추천 — <b>알고리즘이 아니라 배선이다.</b>
 *
 * <p>화면 아래가 통째로 빈 종이였다(검색 버튼을 걷기 전 실측 460px, 화면의 55%). 그 자리를 채우는 데
 * 새 추천 엔진은 필요 없었다 — 필요한 조각이 이미 다 있었기 때문이다:
 * 성향 집계기({@link ReadingProfileAggregator}) · 저자명 정규화({@link WriterName}) ·
 * 알라딘 저자 검색({@link BookService#search}) · 이미 가진 책 판정(isbn 집합).
 *
 * <p>그래서 이건 추천이라기보다 <b>「이어 읽기」</b>다: 내가 읽은(또는 읽는 중인) 책의 저자가 쓴 다른 책.
 *
 * <p>⚠️ <b>{@code ReadingProfileService.profileOf()}를 그대로 쓰지 않는 이유</b>: 그건 책BTI용이라
 * <b>완독 책만</b> 센다. 그대로 쓰면 「책은 담았는데 완독은 아직 0권」인 사람에게 추천이 0건이 되는데,
 * 하필 그 사람들이 이 화면을 가장 많이 본다. 집계기 자체는 상태를 안 가리므로(거르는 쪽이 서비스다)
 * 여기서는 READING+FINISHED를 넘겨 <b>집계기를 그대로 재사용</b>한다 — 새 집계 로직 0줄.
 *
 * <p>협업 필터(「나와 책이 겹치는 사람들이 읽는 책」)는 의도적으로 안 만들었다. 코드는 싸지만
 * 모집단이 PUBLIC 책뿐인데 {@code Book.visibility} 기본값이 PRIVATE이라 대부분에게 0건이 나온다 —
 * 지금 고치려는 빈 화면이 그대로 재발한다.
 */
@Service
public class BookRecommendationService {

    /** 화면에 넘길 최대 권수 — 카드가 안에서 스크롤되므로 창보다 넉넉해야 「더 있다」가 성립한다. */
    static final int RECOMMEND_LIMIT = 8;

    /** 콜드스타트 카드의 제목 — 근거가 없다는 사실을 문구가 정직하게 말한다. */
    static final String FALLBACK_TITLE = "요즘 많이 읽는 책";

    private final BookRepository bookRepository;
    private final BookService bookService;
    private final BookSearchClient searchClient;

    public BookRecommendationService(BookRepository bookRepository, BookService bookService,
                                     BookSearchClient searchClient) {
        this.bookRepository = bookRepository;
        this.bookService = bookService;
        this.searchClient = searchClient;
    }

    /**
     * 이 사용자에게 보여 줄 추천 한 묶음. 뽑을 것이 없으면 {@link BookRecommendation#empty()}.
     *
     * <p>1순위는 내 최다독 저자의 다른 책, 그것이 비면 베스트셀러다. 어느 쪽이든 <b>이미 내 서재에 있는
     * 책은 빠진다</b> — 담은 책을 또 담으라는 추천은 추천이 아니다.
     */
    @Transactional(readOnly = true)
    public BookRecommendation recommendFor(User user) {
        List<Book> mine = readOrFinished(user);
        Set<String> myIsbns = mine.stream()
                .map(Book::getIsbn13).filter(Objects::nonNull).collect(Collectors.toSet());

        Optional<BookRecommendation> byAuthor = recommendByAuthor(mine, myIsbns);
        if (byAuthor.isPresent()) {
            return byAuthor.get();
        }
        List<BookSearchResult> popular = pick(searchClient.bestsellers(), myIsbns);
        if (popular.isEmpty()) {
            return BookRecommendation.empty();
        }
        return new BookRecommendation(FALLBACK_TITLE, "첫 책을 고르는 중이라면", popular);
    }

    /**
     * 읽었거나 읽는 중인 내 책 — 추천의 근거 모집단.
     *
     * <p>담기만 한 책(WANT_TO_READ)은 뺀다. 이유 문구가 「읽으셨네요」라 <b>거짓말이 되기 때문</b>이다 —
     * 데이터와 문구는 같은 것을 가리켜야 한다.
     */
    private List<Book> readOrFinished(User user) {
        return bookRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(b -> b.getStatus() == BookStatus.READING || b.getStatus() == BookStatus.FINISHED)
                .toList();
    }

    private Optional<BookRecommendation> recommendByAuthor(List<Book> mine, Set<String> myIsbns) {
        // 세션은 안 넘긴다 — 여기서 쓰는 것은 topAuthors뿐이고, aggregate는 빈 목록을 허용한다.
        ReadingProfile profile = ReadingProfileAggregator.aggregate(mine, List.of());
        for (LabeledCount author : profile.topAuthors()) {
            List<BookSearchResult> found =
                    pick(bookService.search(author.label(), BookSearchType.AUTHOR, 1).results(), myIsbns);
            if (!found.isEmpty()) {
                return Optional.of(new BookRecommendation(
                        author.label() + "의 다른 책", reasonFor(mine, author.label()), found));
            }
        }
        return Optional.empty();
    }

    /**
     * 「왜 이 저자인가」를 그 사람 자신의 책으로 말한다 — 근거가 안 보이면 추천이 광고로 읽힌다.
     * 같은 저자의 책이 여럿이면 <b>가장 최근에 담은 것</b>을 든다(모집단이 createdAt 내림차순이라 첫 건).
     */
    private String reasonFor(List<Book> mine, String authorLabel) {
        return mine.stream()
                .filter(b -> authorLabel.equals(WriterName.lead(b.getAuthor())))
                .map(b -> "『" + b.getTitle() + "』을 읽으셨네요")
                .findFirst()
                .orElse(null);
    }

    /**
     * 이미 가진 책과 중복 isbn을 걷어 내고 상한까지 자른다.
     *
     * <p>isbn이 없는 결과는 <b>남긴다</b> — 동일성 키가 없어 「가졌다」를 판정할 수 없고, 버리면 멀쩡한
     * 책이 조용히 사라진다(같은 판단이 {@code SearchRow.from}에도 있다).
     */
    private static List<BookSearchResult> pick(List<BookSearchResult> candidates, Set<String> myIsbns) {
        if (candidates == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        return candidates.stream()
                .filter(r -> r.isbn13() == null || !myIsbns.contains(r.isbn13()))
                .filter(r -> r.isbn13() == null || seen.add(r.isbn13())) // 알라딘이 개정판을 겹쳐 줄 때가 있다
                .limit(RECOMMEND_LIMIT)
                .toList();
    }
}
