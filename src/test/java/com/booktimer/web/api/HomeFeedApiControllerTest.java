package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/home-feed — 미니앱 홈 「소식」 박스용 피드 API.
 *
 * <p>노출 게이트(PUBLIC·팔로우·ADMIN·핸들 null)는 {@code BookRepositoryTest}가 쿼리 레벨에서 못 박으므로
 * 여기선 <b>컨트롤러의 몫</b>만 본다: 시작·완독 두 쿼리를 하나로 합쳐 최신순 정렬 + 상한 30건.
 * 「책 뉴스」는 {@code HomeFeedNewsApiControllerTest}(기본 ON의 조인)와
 * {@code HomeFeedNewsDisabledApiControllerTest}(킬스위치 OFF)가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeFeedApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private Clock clock;

    private User saveUser(String email, String loginId, String nickname) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private void publicBook(User owner, String title, Instant startedAt, Instant finishedAt) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.WANT_TO_READ);
        b.changeVisibility(BookVisibility.PUBLIC);
        if (startedAt != null) {
            b.changeStatus(BookStatus.READING, startedAt);
        }
        if (finishedAt != null) {
            b.changeStatus(BookStatus.FINISHED, finishedAt);
        }
        bookRepository.save(b);
    }

    private Instant hoursAgo(long hours) {
        return clock.instant().minus(Duration.ofHours(hours));
    }

    @Test
    @DisplayName("GET /api/home-feed 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/home-feed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("시작·완독 이벤트를 한 목록으로 합쳐 최신순(desc)으로 준다")
    void mergesStartedAndFinishedEventsNewestFirst() throws Exception {
        User me = saveUser("hf-me@booktimer.com", "hfme", "나");
        User followee = saveUser("hf-you@booktimer.com", "hfyou", "친구");
        followRepository.save(Follow.of(me, followee));
        publicBook(followee, "느린책", hoursAgo(50), null);          // 시작 (가장 오래됨)
        publicBook(followee, "빠른책", hoursAgo(10), hoursAgo(1));   // 시작 + 완독

        mockMvc.perform(get("/api/home-feed").with(user("hfme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(3))
                .andExpect(jsonPath("$.social[0].type").value("FINISHED"))
                .andExpect(jsonPath("$.social[0].bookTitle").value("빠른책"))
                .andExpect(jsonPath("$.social[0].loginId").value("hfyou"))
                .andExpect(jsonPath("$.social[0].nickname").value("친구"))
                .andExpect(jsonPath("$.social[1].type").value("STARTED"))
                .andExpect(jsonPath("$.social[1].bookTitle").value("빠른책"))
                .andExpect(jsonPath("$.social[2].type").value("STARTED"))
                .andExpect(jsonPath("$.social[2].bookTitle").value("느린책"));
    }

    @Test
    @DisplayName("이벤트가 많아도 상한 30건까지만 준다 (최신순으로 자른다)")
    void capsAtThirtyEvents() throws Exception {
        User me = saveUser("hf-cap@booktimer.com", "hfcap", "나");
        User followee = saveUser("hf-capyou@booktimer.com", "hfcapyou", "친구");
        followRepository.save(Follow.of(me, followee));
        for (int i = 0; i < 16; i++) { // 16권 × (시작+완독) = 32 이벤트
            publicBook(followee, "책" + i, hoursAgo(100 + i), hoursAgo(50 + i));
        }

        mockMvc.perform(get("/api/home-feed").with(user("hfcap")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social.length()").value(30))
                .andExpect(jsonPath("$.social[0].type").value("FINISHED")); // 가장 최근 = 완독
    }
}
