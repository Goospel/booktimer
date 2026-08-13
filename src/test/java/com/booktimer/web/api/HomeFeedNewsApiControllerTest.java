package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookNews;
import com.booktimer.book.BookNewsRepository;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/home-feed 의 「책 뉴스」 — 네이버 키가 <b>있는</b> 컨텍스트에서의 점등·조인 계약.
 *
 * <p>키가 없는 기본 컨텍스트(= 운영 점등 전)의 OFF 동작은 {@code HomeFeedApiControllerTest}가 본다.
 * 여기선 켜졌을 때 <b>내 완독 책의 뉴스만</b> 실리는지를 본다 — 남의 완독 책 isbn, 내 읽는중 책 isbn의
 * 뉴스를 일부러 픽스처로 심어 결과에서 빠짐을 단언한다(누수 회귀 계측기).
 */
@SpringBootTest(properties = {
        "booktimer.naver.client-id=test-naver-id",
        "booktimer.naver.client-secret=test-naver-secret"
})
@AutoConfigureMockMvc
@Transactional
class HomeFeedNewsApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookNewsRepository bookNewsRepository;

    private User saveUser(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private void saveBook(User owner, String title, String isbn13, BookStatus status) {
        Book b = Book.register(owner, title, "저자", isbn13, null, null, null, BookStatus.WANT_TO_READ);
        b.changeStatus(status, Instant.parse("2026-08-01T00:00:00Z"));
        bookRepository.save(b);
    }

    private void saveNews(String isbn13, String title, String link, String publishedAt) {
        bookNewsRepository.save(BookNews.of(isbn13, title, link, Instant.parse(publishedAt)));
    }

    @Test
    @DisplayName("키가 있으면 newsEnabled=true, 내 완독 책 isbn의 뉴스만 최신순으로 실린다")
    void servesNewsOfMyFinishedBooksOnly() throws Exception {
        User me = saveUser("news-me@booktimer.com", "newsme");
        User other = saveUser("news-other@booktimer.com", "newsother");

        saveBook(me, "내완독책", "9780000000001", BookStatus.FINISHED);
        saveBook(me, "내읽는중책", "9780000000002", BookStatus.READING);
        saveBook(other, "남의완독책", "9780000000003", BookStatus.FINISHED);

        saveNews("9780000000001", "오래된 기사", "https://n.news.naver.com/old", "2026-08-01T00:00:00Z");
        saveNews("9780000000001", "새 기사", "https://n.news.naver.com/new", "2026-08-10T00:00:00Z");
        saveNews("9780000000002", "읽는중책 기사", "https://n.news.naver.com/reading", "2026-08-11T00:00:00Z");
        saveNews("9780000000003", "남의책 기사", "https://n.news.naver.com/other", "2026-08-12T00:00:00Z");

        mockMvc.perform(get("/api/home-feed").with(user("newsme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsEnabled").value(true))
                .andExpect(jsonPath("$.news.length()").value(2))
                .andExpect(jsonPath("$.news[0].title").value("새 기사"))
                .andExpect(jsonPath("$.news[0].link").value("https://n.news.naver.com/new"))
                .andExpect(jsonPath("$.news[0].bookTitle").value("내완독책"))
                .andExpect(jsonPath("$.news[1].title").value("오래된 기사"));
    }

    @Test
    @DisplayName("완독한 책이 없으면 뉴스는 빈 목록 (탭은 켜지되 빈 상태)")
    void emptyWhenNoFinishedBooks() throws Exception {
        saveUser("news-empty@booktimer.com", "newsempty");
        saveNews("9780000000009", "아무 기사", "https://n.news.naver.com/any", "2026-08-12T00:00:00Z");

        mockMvc.perform(get("/api/home-feed").with(user("newsempty")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsEnabled").value(true))
                .andExpect(jsonPath("$.news.length()").value(0));
    }
}
