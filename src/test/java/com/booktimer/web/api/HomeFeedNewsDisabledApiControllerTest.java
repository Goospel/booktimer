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
 * GET /api/home-feed 의 「책 뉴스」 킬스위치 OFF — {@code booktimer.news.enabled=false}.
 *
 * <p>구글 뉴스 RSS는 공식 API가 아니라 형식이 언제든 바뀔 수 있다. 그때 <b>SSM 값 하나로</b> 탭을
 * 통째로 끌 수 있어야 하고, 끄면 <b>캐시에 기사가 남아 있어도</b> 새지 않아야 한다 — 그래서
 * 픽스처로 내 완독 책의 기사를 심어 두고 빈 목록임을 단언한다(꺼짐이 진짜 꺼짐인지의 계측기).
 */
@SpringBootTest(properties = "booktimer.news.enabled=false")
@AutoConfigureMockMvc
@Transactional
class HomeFeedNewsDisabledApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookNewsRepository bookNewsRepository;

    @Test
    @DisplayName("킬스위치가 꺼지면 newsEnabled=false, 캐시에 기사가 있어도 news=[]")
    void newsIsGatedOff() throws Exception {
        User me = User.of("news-off@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv",
                "책벌레", "Asia/Seoul", Role.USER);
        me.assignLoginId("newsoff");
        userRepository.save(me);
        Book book = Book.register(me, "내완독책", "저자", "9780000000011", null, null, null,
                BookStatus.WANT_TO_READ);
        book.changeStatus(BookStatus.FINISHED, Instant.parse("2026-08-01T00:00:00Z"));
        bookRepository.save(book);
        bookNewsRepository.save(BookNews.of("9780000000011", "기사",
                "https://news.google.com/rss/articles/off", Instant.parse("2026-08-10T00:00:00Z"), "연합뉴스"));

        mockMvc.perform(get("/api/home-feed").with(user("newsoff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newsEnabled").value(false))
                .andExpect(jsonPath("$.news.length()").value(0));
    }
}
