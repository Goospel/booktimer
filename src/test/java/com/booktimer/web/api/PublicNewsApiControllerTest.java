package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookNews;
import com.booktimer.book.BookNewsRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.book.GeneralBookNewsFeed;
import com.booktimer.book.GoogleNewsRssClient.NewsArticle;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/public/news — 게스트(토큰 없음)에게 여는 「일반 책 뉴스」. 응답은 {@link GeneralBookNewsFeed} 스냅샷뿐이다.
 *
 * <p>핵심 계측기는 {@link #responseIgnoresUserBooks()}다(리뷰 차단 B-1): 사용자가 어떤 책·뉴스 캐시를 만들어도
 * 게스트 응답이 한 바이트도 안 바뀌어야 한다. 스냅샷은 스케줄 잡 대신 {@code replaceForTest}로 채운다 —
 * 테스트 컨텍스트는 첫 새로고침을 하루 뒤로 밀어 실네트워크를 부르지 않는다(테스트 프로퍼티).
 */
@SpringBootTest(properties = "booktimer.miniapp.allowed-origins=https://miniapp.test")
@AutoConfigureMockMvc
@Transactional
class PublicNewsApiControllerTest {

    private static final String EVIL = "!!무료 포인트 받기 evil.example";

    @Autowired MockMvc mockMvc;
    @Autowired GeneralBookNewsFeed feed;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired BookNewsRepository bookNewsRepository;

    @AfterEach
    void clearSnapshot() {
        feed.replaceForTest(List.of()); // 싱글턴 빈이라 컨텍스트 캐시를 공유하는 다른 테스트로 새지 않게
    }

    private static GeneralBookNewsFeed.Item item(String topic, String title, String linkId, String publishedAt) {
        return new GeneralBookNewsFeed.Item(topic, new NewsArticle(title,
                "https://news.google.com/rss/articles/" + linkId, Instant.parse(publishedAt), "연합뉴스"));
    }

    private void seedTwo() {
        feed.replaceForTest(List.of(
                item("신간", "[신간] 공간은 어떻게 권력이 되는가", "shingan", "2026-09-16T01:00:00Z"),
                item("출판계", "출판 진흥 예산 645억원", "chulpan", "2026-09-15T01:00:00Z")));
    }

    @Test
    @DisplayName("스냅샷을 NewsItem으로 — bookTitle은 주제 라벨, 10분 캐시")
    void servesSnapshotAsNewsItems() throws Exception {
        seedTwo();

        mockMvc.perform(get("/api/public/news"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=600")))
                .andExpect(jsonPath("$.newsEnabled").value(true))
                .andExpect(jsonPath("$.news.length()").value(2))
                .andExpect(jsonPath("$.news[0].bookTitle").value("신간"))
                .andExpect(jsonPath("$.news[0].title").value("[신간] 공간은 어떻게 권력이 되는가"))
                .andExpect(jsonPath("$.news[0].link").value("https://news.google.com/rss/articles/shingan"))
                .andExpect(jsonPath("$.news[0].source").value("연합뉴스"))
                .andExpect(jsonPath("$.news[0].publishedAt").value("2026-09-16T01:00:00Z"))
                .andExpect(jsonPath("$.news[1].bookTitle").value("출판계"));
    }

    @Test
    @DisplayName("B-1 계측기: 사용자가 공개 완독 책·뉴스 캐시를 만들어도 게스트 응답은 바이트 동일")
    void responseIgnoresUserBooks() throws Exception {
        seedTwo(); // 비어 있으면 「전후 동일」이 공허하게 참이다
        String before = mockMvc.perform(get("/api/public/news")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        seedAttackerBookAndNews();

        String after = mockMvc.perform(get("/api/public/news")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(after).isEqualTo(before).doesNotContain("evil.example");

        // 양성 대조군 — 같은 픽스처가 그 사용자 본인의 홈 뉴스엔 실제로 나온다(픽스처에 노출력이 있다).
        String own = mockMvc.perform(get("/api/home-feed").with(user("pubevil"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(own).contains("evil.example");
    }

    @Test
    @DisplayName("스냅샷이 비었으면(기동 직후) 켜진 채 빈 목록 — 사용자 책·뉴스 캐시로 채우지 않고, 공개 캐시하지 않는다")
    void emptySnapshot_enabledEmpty() throws Exception {
        // 빈 스냅샷 경로에도 사용자 데이터가 섞이지 않는지 — 공격자 픽스처가 있어야 폴백 혼입을 잡는다.
        seedAttackerBookAndNews();

        mockMvc.perform(get("/api/public/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsEnabled").value(true))
                .andExpect(jsonPath("$.news.length()").value(0))
                // 빈 응답을 공개 캐시하면 수집이 끝난 뒤에도 WebView가 최대 10분 잠금 화면을 본다.
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    private void seedAttackerBookAndNews() {
        User attacker = User.of("pub-evil@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        attacker.assignLoginId("pubevil");
        userRepository.save(attacker);
        Book book = Book.register(attacker, EVIL, "저자", "9780000000201", null, null, null, BookStatus.WANT_TO_READ);
        book.changeStatus(BookStatus.FINISHED, Instant.parse("2026-09-01T00:00:00Z"));
        book.changeVisibility(BookVisibility.PUBLIC);
        bookRepository.save(book);
        bookNewsRepository.save(BookNews.of("9780000000201", EVIL + " 기사",
                "https://news.google.com/rss/articles/evil", Instant.parse("2026-09-16T05:00:00Z"), "연합뉴스"));
    }

    @Test
    @DisplayName("CORS: 허용된 미니앱 오리진이면 ACAO 헤더가 실린다 (토큰 없는 게스트 WebView가 읽을 수 있다)")
    void corsAllowedOrigin_hasHeader() throws Exception {
        mockMvc.perform(get("/api/public/news").header("Origin", "https://miniapp.test"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://miniapp.test"));
    }

    @Test
    @DisplayName("CORS: 허용되지 않은 오리진은 403 + ACAO 없음 (헤더 부재만으론 302와 구분이 안 된다)")
    void corsOtherOrigin_forbidden() throws Exception {
        mockMvc.perform(get("/api/public/news").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
