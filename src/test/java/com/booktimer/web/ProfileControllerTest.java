package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.FollowService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 개인 프로필 페이지 컨트롤러 통합 테스트 (SNS 2단계, sns-design §7.2).
 *
 * <p>핵심: 이 페이지는 "남에게 보이는 공개 프로필"이라 <b>viewer 무관하게 PUBLIC 책만</b> 노출한다
 * (본인이 자기 프로필을 봐도 PUBLIC만 — 공개 미리보기). 비로그인은 차단(로그인 한정 시작).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private FollowService followService;

    private User newUser(String email, String nickname) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.USER));
    }

    private void publicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.makePublic();
        bookRepository.save(b);
    }

    private void privateBook(User owner, String title) {
        bookRepository.save(Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    @SuppressWarnings("unchecked")
    private List<Book> booksInModel(MvcResult res) {
        return (List<Book>) res.getModelAndView().getModel().get("books");
    }

    @Test
    @DisplayName("GET /u/{nickname}: 없는 닉네임이면 404")
    void profile_unknownNickname_404() throws Exception {
        newUser("viewer@booktimer.com", "뷰어");
        mockMvc.perform(get("/u/{nickname}", "존재하지않는닉").with(user("viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /u/{nickname}: 타인 프로필은 PUBLIC 책만 노출하고 PRIVATE 책은 누락된다")
    void profile_otherUser_onlyPublicBooks() throws Exception {
        newUser("viewer@booktimer.com", "뷰어");
        User owner = newUser("owner@booktimer.com", "공개왕");
        publicBook(owner, "공개한 책");
        privateBook(owner, "비공개 책");

        MvcResult res = mockMvc.perform(get("/u/{nickname}", "공개왕").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andReturn();

        assertThat(booksInModel(res)).extracting(Book::getTitle).containsExactly("공개한 책");
    }

    @Test
    @DisplayName("GET /u/{nickname}: 본인이 자기 프로필을 봐도 PUBLIC만 (공개 미리보기)")
    void profile_self_onlyPublic() throws Exception {
        User me = newUser("me@booktimer.com", "나자신");
        publicBook(me, "내 공개책");
        privateBook(me, "내 비공개책");

        MvcResult res = mockMvc.perform(get("/u/{nickname}", "나자신").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(booksInModel(res)).extracting(Book::getTitle).containsExactly("내 공개책");
    }

    @Test
    @DisplayName("GET /u/{nickname}: 팔로워/팔로잉 카운트와 팔로우 상태가 모델에 실린다")
    void profile_followInfo() throws Exception {
        User viewer = newUser("viewer@booktimer.com", "뷰어");
        User owner = newUser("owner@booktimer.com", "주인");
        User third = newUser("third@booktimer.com", "삼자");
        followService.follow(viewer, owner); // 내가 팔로우
        followService.follow(third, owner);  // 다른 사람도 팔로우 → 팔로워 2

        mockMvc.perform(get("/u/{nickname}", "주인").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("followerCount", 2L))
                .andExpect(model().attribute("following", true))
                .andExpect(model().attribute("self", false));
    }

    @Test
    @DisplayName("GET /u/{nickname}: 본인 프로필은 self=true (팔로우 버튼 없음)")
    void profile_self_noFollowButton() throws Exception {
        newUser("me@booktimer.com", "나자신");

        mockMvc.perform(get("/u/{nickname}", "나자신").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("self", true))
                .andExpect(model().attribute("following", false));
    }

    @Test
    @DisplayName("비로그인 사용자는 프로필을 볼 수 없다 — 로그인으로 리다이렉트(로그인 한정 시작)")
    void profile_anonymous_redirectsToLogin() throws Exception {
        newUser("owner2@booktimer.com", "공개왕2");

        mockMvc.perform(get("/u/{nickname}", "공개왕2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
