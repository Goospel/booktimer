package com.booktimer.web.api;

import com.booktimer.block.BlockService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/home-feed 의 {@code discover} — 홈 「여백」 탭(팔로우 무관 공개 여백).
 *
 * <p><b>「소식」과 정확히 반대편이다</b>: 소식은 팔로우한 사람만 보고, 여기는 팔로우를 아예 안 본다.
 * 그래서 소식이 팔로우 불변식에 기대 생략했던 <b>차단 필터가 여기선 쿼리에 필수</b>이고, 「올린 글만」
 * ({@code shared = true})이라는 「모두의 여백」의 술어를 그대로 진다.
 *
 * <p>순서는 <b>서버가 섞는다</b>(발견용) — 그래서 이 파일의 단언은 순서가 아니라 <b>집합</b>이다.
 * 순서를 단언하면 셔플을 지워도 초록인 공허한 테스트가 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeFeedDiscoverApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private BlockService blockService;

    private User saveUser(String email, String loginId, String nickname) {
        return saveUser(email, loginId, nickname, Role.USER);
    }

    private User saveUser(String email, String loginId, String nickname, Role role) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", role);
        if (loginId != null) {
            u.assignLoginId(loginId);
        }
        return userRepository.save(u);
    }

    private Book book(User owner, String title, BookVisibility visibility) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.changeVisibility(visibility);
        return bookRepository.save(b);
    }

    /** 「모두의 여백」에 올린 글 — 이 탭이 보는 것은 이것뿐이다. */
    private Story sharedStory(User author, Book book, String text) {
        Story s = Story.of(author, text, book, null);
        s.markShared(true);
        return storyRepository.save(s);
    }

    /** 응답의 여백 문장 집합 — 순서는 셔플이라 단언하지 않는다. */
    private List<String> discoverExcerpts(String loginId) throws Exception {
        String json = mockMvc.perform(get("/api/home-feed").with(user(loginId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.discover[*].excerpt");
    }

    @Test
    @DisplayName("팔로우하지 않은 낯선 사람의 올린 글이 실린다 — 이 탭의 존재 이유")
    void includesSharedStoriesOfStrangers() throws Exception {
        saveUser("dc-a@booktimer.com", "dca", "나");
        User stranger = saveUser("dc-a2@booktimer.com", "dca2", "낯선사람");
        Book b = book(stranger, "데미안", BookVisibility.PUBLIC);
        sharedStory(stranger, b, "새는 알에서 나오려고 투쟁한다");

        mockMvc.perform(get("/api/home-feed").with(user("dca")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discover.length()").value(1))
                .andExpect(jsonPath("$.discover[0].loginId").value("dca2"))
                .andExpect(jsonPath("$.discover[0].nickname").value("낯선사람"))
                .andExpect(jsonPath("$.discover[0].bookTitle").value("데미안"))
                .andExpect(jsonPath("$.discover[0].type").value("STORY"))
                .andExpect(jsonPath("$.discover[0].bookId").value(b.getId()))
                .andExpect(jsonPath("$.discover[0].excerpt").value("새는 알에서 나오려고 투쟁한다"))
                // 묶지 않는다 — 글 1장이 1행이라 count는 언제나 1이다.
                .andExpect(jsonPath("$.discover[0].count").value(1));
    }

    @Test
    @DisplayName("올리지 않은 글은 빠진다 — 「모두의 여백」에 올린 글만 본다")
    void excludesUnsharedStories() throws Exception {
        saveUser("dc-b@booktimer.com", "dcb", "나");
        User stranger = saveUser("dc-b2@booktimer.com", "dcb2", "낯선사람");
        Book b = book(stranger, "혼자 보는 책", BookVisibility.PUBLIC);
        storyRepository.save(Story.of(stranger, "안 올린 글", b, null)); // shared = false

        assertThat(discoverExcerpts("dcb")).isEmpty();
    }

    @Test
    @DisplayName("비공개 책의 글은 빠진다 — 올린 뒤 비공개로 돌려도 표시 시점에 다시 본다")
    void excludesStoriesOnPrivateBooks() throws Exception {
        saveUser("dc-c@booktimer.com", "dcc", "나");
        User stranger = saveUser("dc-c2@booktimer.com", "dcc2", "낯선사람");
        Book b = book(stranger, "나중에 비공개가 될 책", BookVisibility.PUBLIC);
        sharedStory(stranger, b, "새면 안 되는 문장");
        b.makePrivate();
        bookRepository.save(b);

        assertThat(discoverExcerpts("dcc")).isEmpty();
    }

    @Test
    @DisplayName("내 글은 빠진다 — 발견 탭에서 내 글을 발견할 일은 없다")
    void excludesMyOwnStories() throws Exception {
        User me = saveUser("dc-d@booktimer.com", "dcd", "나");
        User stranger = saveUser("dc-d2@booktimer.com", "dcd2", "낯선사람");
        sharedStory(me, book(me, "내 책", BookVisibility.PUBLIC), "내가 쓴 글");
        sharedStory(stranger, book(stranger, "남의 책", BookVisibility.PUBLIC), "남이 쓴 글");

        assertThat(discoverExcerpts("dcd")).containsExactly("남이 쓴 글");
    }

    @Test
    @DisplayName("내가 차단한 사람의 글은 빠진다 — 팔로우 불변식이 없는 목록이라 쿼리가 막아야 한다")
    void excludesStoriesOfUsersIBlocked() throws Exception {
        User me = saveUser("dc-e@booktimer.com", "dce", "나");
        User blocked = saveUser("dc-e2@booktimer.com", "dce2", "차단한사람");
        sharedStory(blocked, book(blocked, "차단한 사람 책", BookVisibility.PUBLIC), "안 보여야 하는 글");
        blockService.block(me, blocked);

        assertThat(discoverExcerpts("dce")).isEmpty();
    }

    @Test
    @DisplayName("나를 차단한 사람의 글도 빠진다 — 차단은 양방향으로 가린다")
    void excludesStoriesOfUsersWhoBlockedMe() throws Exception {
        User me = saveUser("dc-f@booktimer.com", "dcf", "나");
        User blocker = saveUser("dc-f2@booktimer.com", "dcf2", "나를차단한사람");
        sharedStory(blocker, book(blocker, "차단자 책", BookVisibility.PUBLIC), "안 보여야 하는 글");
        blockService.block(blocker, me);

        assertThat(discoverExcerpts("dcf")).isEmpty();
    }

    @Test
    @DisplayName("ADMIN 작성자의 글은 빠진다 — 운영 계정을 사용자 목록에 세우지 않는다(N-055)")
    void excludesAdminAuthors() throws Exception {
        saveUser("dc-j@booktimer.com", "dcj", "나");
        User admin = saveUser("dc-j2@booktimer.com", "dcj2", "관리자", Role.ADMIN);
        sharedStory(admin, book(admin, "관리자 책", BookVisibility.PUBLIC), "관리자가 쓴 글");

        assertThat(discoverExcerpts("dcj")).isEmpty();
    }

    @Test
    @DisplayName("공개 핸들(loginId)이 없는 작성자의 글은 빠진다 — 온보딩 전 계정 노출 금지")
    void excludesAuthorsWithoutLoginId() throws Exception {
        saveUser("dc-g@booktimer.com", "dcg", "나");
        User onboarding = saveUser("dc-g2@booktimer.com", null, "온보딩전");
        sharedStory(onboarding, book(onboarding, "핸들 없는 사람 책", BookVisibility.PUBLIC), "핸들 없는 글");

        assertThat(discoverExcerpts("dcg")).isEmpty();
    }

    @Test
    @DisplayName("31장이면 30장으로 자른다 — 상한은 소식·뉴스와 같다")
    void capsAtThirty() throws Exception {
        saveUser("dc-h@booktimer.com", "dch", "나");
        User stranger = saveUser("dc-h2@booktimer.com", "dch2", "낯선사람");
        Book b = book(stranger, "글이 많은 책", BookVisibility.PUBLIC);
        for (int i = 0; i < 31; i++) {
            sharedStory(stranger, b, "글 " + i);
        }

        mockMvc.perform(get("/api/home-feed").with(user("dch")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discover.length()").value(30));
    }

    @Test
    @DisplayName("같은 사람이 한 책에 쓴 글도 묶지 않는다 — 발견 탭은 글 단위다(소식과 다른 점)")
    void doesNotGroupByPerson() throws Exception {
        saveUser("dc-i@booktimer.com", "dci", "나");
        User stranger = saveUser("dc-i2@booktimer.com", "dci2", "낯선사람");
        Book b = book(stranger, "한 권", BookVisibility.PUBLIC);
        sharedStory(stranger, b, "첫 글");
        sharedStory(stranger, b, "둘째 글");

        assertThat(discoverExcerpts("dci")).containsExactlyInAnyOrder("첫 글", "둘째 글");
    }
}
