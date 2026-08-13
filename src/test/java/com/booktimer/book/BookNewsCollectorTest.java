package com.booktimer.book;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 뉴스 수집 배치의 행동 계약 (H2 슬라이스) — 외부 HTTP는 스텁으로 대체해 네트워크 없이 본다.
 *
 * <p>못 박는 것: (1) 수집 대상 선별(완독 + isbn·저자 있는 책만), (2) isbn당 API 1회 + 매처 필터,
 * (3) 상위 3건, (4) delete-then-insert 멱등(두 번 돌려도 중복 없음), (5) 키 없으면 no-op.
 */
@DataJpaTest
@Import(JpaConfig.class)
class BookNewsCollectorTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookNewsRepository bookNewsRepository;

    @Autowired
    private UserRepository userRepository;

    /** 호출된 쿼리를 기록하고 미리 정한 기사 목록을 돌려주는 스텁 — 네트워크 없음. */
    private static final class StubClient extends NaverNewsClient {
        private final List<String> queries = new ArrayList<>();
        private final List<NewsArticle> articles;

        StubClient(String clientId, String clientSecret, List<NewsArticle> articles) {
            super(clientId, clientSecret);
            this.articles = articles;
        }

        @Override
        public List<NewsArticle> search(String query) {
            queries.add(query);
            return articles;
        }
    }

    private static NaverNewsClient.NewsArticle article(String suffix, int hour) {
        return new NaverNewsClient.NewsArticle(
                "총, 균, 쇠 이야기 " + suffix,
                "재레드 다이아몬드의 대표작",
                "https://n.news.naver.com/" + suffix,
                Instant.parse("2026-08-11T0%d:00:00Z".formatted(hour)));
    }

    private User saveUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    private Book saveBook(User owner, String title, String author, String isbn13, BookStatus status) {
        Book b = Book.register(owner, title, author, isbn13, null, null, null, BookStatus.WANT_TO_READ);
        b.changeStatus(status, Instant.parse("2026-08-01T00:00:00Z"));
        return bookRepository.save(b);
    }

    @Test
    @DisplayName("키가 없으면 아무것도 하지 않는다 — 외부 호출 0, 저장 0 (키 없이 배포해도 안전)")
    void noOp_whenDisabled() {
        User me = saveUser("news-off@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient("", "", List.of(article("a", 1)));

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).isEmpty();
        assertThat(bookNewsRepository.count()).isZero();
    }

    @Test
    @DisplayName("완독 + isbn·저자가 모두 있는 책만 수집 대상 — 읽는중·저자null·isbn null은 제외")
    void collectsOnlyEligibleBooks() {
        User me = saveUser("news-target@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        saveBook(me, "읽는중책", "저자", "9791111111111", BookStatus.READING);
        saveBook(me, "저자없는책", null, "9792222222222", BookStatus.FINISHED);
        saveBook(me, "isbn없는책", "저자", null, BookStatus.FINISHED);
        StubClient client = new StubClient("id", "secret", List.of(article("a", 1)));

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).containsExactly("\"총, 균, 쇠\" 재레드 다이아몬드");
    }

    @Test
    @DisplayName("여러 사용자가 같은 isbn을 완독해도 isbn당 API 1회 — 쿼터·중복 절약")
    void callsOncePerIsbn() {
        User a = saveUser("news-a@booktimer.com");
        User b = saveUser("news-b@booktimer.com");
        saveBook(a, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        saveBook(b, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient("id", "secret", List.of(article("a", 1)));

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).hasSize(1);
    }

    @Test
    @DisplayName("매처를 통과한 기사만, isbn당 상위 3건까지 저장한다")
    void keepsTopThreeMatchingArticles() {
        User me = saveUser("news-top3@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        List<NaverNewsClient.NewsArticle> articles = List.of(
                // 저자가 없어 매처가 기각해야 하는 기사 — 상위 3건 안에 들도록 일부러 맨 앞에 둔다
                // (뒤에 두면 상한 3건에 밀려 자연히 빠지므로 필터를 검증하지 못한다).
                new NaverNewsClient.NewsArticle("총, 균, 쇠 관련 잡담", "저자 언급 없음",
                        "https://n.news.naver.com/x", Instant.parse("2026-08-11T05:00:00Z")),
                article("1", 1), article("2", 2), article("3", 3), article("4", 4));
        StubClient client = new StubClient("id", "secret", articles);

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        List<BookNews> saved = bookNewsRepository.findAll();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(BookNews::getLink)
                .containsExactlyInAnyOrder("https://n.news.naver.com/1",
                        "https://n.news.naver.com/2", "https://n.news.naver.com/3");
        assertThat(saved).allMatch(n -> n.getIsbn13().equals("9788970127248"));
    }

    @Test
    @DisplayName("두 번 돌려도 중복이 쌓이지 않는다 — delete-then-insert 멱등")
    void isIdempotent() {
        User me = saveUser("news-idem@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient("id", "secret", List.of(article("a", 1), article("b", 2)));
        BookNewsCollector collector = new BookNewsCollector(bookRepository, bookNewsRepository, client);

        collector.collect();
        collector.collect();

        assertThat(bookNewsRepository.findAll()).hasSize(2);
    }
}
