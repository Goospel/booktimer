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
 * <p>못 박는 것: (1) 수집 대상 선별(완독 + isbn·저자 있는 책만), (2) isbn당 호출 1회 + 매처 필터,
 * (3) 상위 3건, (4) delete-then-insert 멱등(두 번 돌려도 중복 없음), (5) 킬스위치 OFF면 no-op.
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
    private static final class StubClient extends GoogleNewsRssClient {
        private final List<String> queries = new ArrayList<>();
        private final List<NewsArticle> articles;

        StubClient(boolean enabled, List<NewsArticle> articles) {
            super(enabled);
            this.articles = articles;
        }

        @Override
        public List<NewsArticle> search(String query) {
            queries.add(query);
            return articles;
        }
    }

    private static GoogleNewsRssClient.NewsArticle article(String suffix, int hour) {
        return new GoogleNewsRssClient.NewsArticle(
                "총, 균, 쇠 이야기 " + suffix + " — 재레드 다이아몬드",
                "https://news.google.com/rss/articles/" + suffix,
                Instant.parse("2026-08-11T0%d:00:00Z".formatted(hour)),
                "연합뉴스");
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
    @DisplayName("킬스위치가 꺼지면 아무것도 하지 않는다 — 외부 호출 0, 저장 0 (SSM 값 하나로 끈다)")
    void noOp_whenDisabled() {
        User me = saveUser("news-off@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient(false, List.of(article("a", 1)));

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).isEmpty();
        assertThat(bookNewsRepository.count()).isZero();
    }

    /*
     * 결과를 돌려주는 이유는 관리자 수동 실행(POST /admin/books/collect-news) 때문이다 — 새벽 배치를
     * 기다리지 않고 손으로 돌릴 때 "몇 종을 훑어 몇 건 저장했는지"가 안 보이면 눌러도 됐는지 알 수 없다.
     * enabled를 따로 싣는 건 킬스위치가 내려간 상태의 0종 0건이 "완독한 책이 없음"과 구분되지 않아서다.
     */
    /*
     * 아래 두 상수는 알라딘 API 원문 형식이다(운영 DB 실값과 같은 꼴) — 부제·역할표기가 붙어 있다.
     * 2026-08-14 사고: 이 원문을 그대로 질의·매칭에 써서 운영에서 29종 전부 0건이었다. 수집기 테스트가
     * 이상적인 값("총, 균, 쇠"/"재레드 다이아몬드")만 써서 못 잡았으므로, 최소 두 경로(질의 생성·저장)를
     * 원문으로 못 박는다.
     */
    private static final String RAW_TITLE = "총, 균, 쇠 - 무기·병균·금속은 인류의 운명을 어떻게 바꿨는가";
    private static final String RAW_AUTHOR = "재레드 다이아몬드 (지은이), 김진준 (옮긴이)";

    @Test
    @DisplayName("결과로 대상 종수·저장 건수를 돌려준다 — 관리자 수동 실행의 보고용 (알라딘 원문 메타)")
    void collect_reportsCounts() {
        User me = saveUser("news-count@booktimer.com");
        saveBook(me, RAW_TITLE, RAW_AUTHOR, "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient(true, List.of(article("총, 균, 쇠 재레드 다이아몬드 특강", 1)));

        NewsCollectionResult result = new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(result.enabled()).isTrue();
        assertThat(result.targets()).isEqualTo(1);
        assertThat(result.saved()).isEqualTo(1);
    }

    @Test
    @DisplayName("킬스위치가 꺼져 있으면 결과가 그 사실을 말한다 — 0종 0건과 구분돼야 한다")
    void collect_reportsDisabled() {
        User me = saveUser("news-count-off@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient(false, List.of(article("a", 1)));

        NewsCollectionResult result = new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(result.enabled()).isFalse();
    }

    @Test
    @DisplayName("완독 + isbn·저자가 모두 있는 책만 수집 대상 — 읽는중·저자null·isbn null은 제외")
    void collectsOnlyEligibleBooks() {
        User me = saveUser("news-target@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        saveBook(me, "읽는중책", "저자", "9791111111111", BookStatus.READING);
        saveBook(me, "저자없는책", null, "9792222222222", BookStatus.FINISHED);
        saveBook(me, "isbn없는책", "저자", null, BookStatus.FINISHED);
        StubClient client = new StubClient(true, List.of(article("a", 1)));

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).containsExactly("총, 균, 쇠 재레드 다이아몬드");
    }

    @Test
    @DisplayName("질의는 알라딘 원문이 아니라 정규화한 제목·저자로 만든다 — 부제·역할표기가 검색을 망친다")
    void buildsQueryFromNormalizedMetadata() {
        User me = saveUser("news-raw-query@booktimer.com");
        saveBook(me, RAW_TITLE, RAW_AUTHOR, "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient(true, List.of(article("a", 1)));

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).containsExactly("총, 균, 쇠 재레드 다이아몬드");
    }

    @Test
    @DisplayName("정규화 결과가 빈 책(제목이 통째로 괄호·역자뿐인 저자)은 수집 대상에서 제외 — 호출 0")
    void excludesBooksWithEmptyNormalizedMetadata() {
        User me = saveUser("news-pathological@booktimer.com");
        saveBook(me, "(전2권)", "재레드 다이아몬드 (지은이)", "9793333333333", BookStatus.FINISHED);
        saveBook(me, "총, 균, 쇠", "김진준 (옮긴이)", "9794444444444", BookStatus.FINISHED);
        StubClient client = new StubClient(true, List.of(article("a", 1)));

        NewsCollectionResult result =
                new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).isEmpty();
        assertThat(result.targets()).isZero();
    }

    @Test
    @DisplayName("여러 사용자가 같은 isbn을 완독해도 isbn당 API 1회 — 쿼터·중복 절약")
    void callsOncePerIsbn() {
        User a = saveUser("news-a@booktimer.com");
        User b = saveUser("news-b@booktimer.com");
        saveBook(a, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        saveBook(b, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient(true, List.of(article("a", 1)));

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        assertThat(client.queries).hasSize(1);
    }

    @Test
    @DisplayName("매처를 통과한 기사만, isbn당 상위 3건까지 저장한다 (출처도 함께 저장)")
    void keepsTopThreeMatchingArticles() {
        User me = saveUser("news-top3@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        List<GoogleNewsRssClient.NewsArticle> articles = List.of(
                // 저자가 없어 매처가 기각해야 하는 기사 — 상위 3건 안에 들도록 일부러 맨 앞에 둔다
                // (뒤에 두면 상한 3건에 밀려 자연히 빠지므로 필터를 검증하지 못한다).
                new GoogleNewsRssClient.NewsArticle("총, 균, 쇠 관련 잡담",
                        "https://news.google.com/rss/articles/x",
                        Instant.parse("2026-08-11T05:00:00Z"), "연합뉴스"),
                article("1", 1), article("2", 2), article("3", 3), article("4", 4));
        StubClient client = new StubClient(true, articles);

        new BookNewsCollector(bookRepository, bookNewsRepository, client).collect();

        List<BookNews> saved = bookNewsRepository.findAll();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(BookNews::getLink)
                .containsExactlyInAnyOrder("https://news.google.com/rss/articles/1",
                        "https://news.google.com/rss/articles/2", "https://news.google.com/rss/articles/3");
        assertThat(saved).allMatch(n -> n.getIsbn13().equals("9788970127248"));
        assertThat(saved).allMatch(n -> "연합뉴스".equals(n.getSource()));
    }

    @Test
    @DisplayName("두 번 돌려도 중복이 쌓이지 않는다 — delete-then-insert 멱등")
    void isIdempotent() {
        User me = saveUser("news-idem@booktimer.com");
        saveBook(me, "총, 균, 쇠", "재레드 다이아몬드", "9788970127248", BookStatus.FINISHED);
        StubClient client = new StubClient(true, List.of(article("a", 1), article("b", 2)));
        BookNewsCollector collector = new BookNewsCollector(bookRepository, bookNewsRepository, client);

        collector.collect();
        collector.collect();

        assertThat(bookNewsRepository.findAll()).hasSize(2);
    }
}
