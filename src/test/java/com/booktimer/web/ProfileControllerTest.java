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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 개인 프로필 페이지 컨트롤러 통합 테스트 (SNS 2단계 · login-id-design §7 PR-3).
 *
 * <p>프로필은 이제 <b>login_id(공개 @핸들)로 조회</b>한다({@code GET /u/{loginId}}) — 닉네임은 중복될 수
 * 있어 더 이상 1:1 핸들이 아니다. 이 페이지는 "남에게 보이는 공개 프로필"이라 <b>viewer 무관하게 PUBLIC
 * 책만</b> 노출한다(본인이 봐도 PUBLIC만). 비로그인은 차단(로그인 한정 시작).
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

    private User newUser(String email, String loginId, String nickname) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private User newAdmin(String email, String loginId, String nickname) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private void publicBook(User owner, String title) {
        publicBook(owner, title, BookStatus.READING);
    }

    private void publicBook(User owner, String title, BookStatus status) {
        Book b = Book.register(owner, title, null, null, null, null, null, status);
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
    @DisplayName("GET /u/{loginId}: 없는 아이디면 404")
    void profile_unknownLoginId_404() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        mockMvc.perform(get("/u/{loginId}", "nosuchid").with(user("viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /u/{loginId}: 운영자(ADMIN) 프로필은 핸들을 직접 알아도 404 (존재 누설 회피 — 차단과 동일 처리)")
    void profile_admin_404() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        newAdmin("admin@booktimer.com", "adminhandle", "운영자");

        // 핸들을 정확히 알아도 운영자 프로필은 노출되지 않는다(검색 제외와 일관 — 존재 자체를 숨김)
        mockMvc.perform(get("/u/{loginId}", "adminhandle").with(user("viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /u/{loginId}: 운영자 본인이 자기 프로필을 조회해도 404 (소셜 프로필 비대상)")
    void profile_adminSelf_404() throws Exception {
        newAdmin("admin@booktimer.com", "adminhandle", "운영자");

        mockMvc.perform(get("/u/{loginId}", "adminhandle").with(user("adminhandle")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /u/{loginId}: 타인 프로필은 PUBLIC 책만 노출하고 PRIVATE 책은 누락된다")
    void profile_otherUser_onlyPublicBooks() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        publicBook(owner, "공개한 책");
        privateBook(owner, "비공개 책");

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attribute("loginId", "openking"))
                .andExpect(model().attribute("nickname", "공개왕"))
                .andReturn();

        assertThat(booksInModel(res)).extracting(Book::getTitle).containsExactly("공개한 책");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 닉네임이 같아도 login_id로 각각 정확히 조회된다(핸들은 login_id)")
    void profile_duplicateNickname_resolvedByLoginId() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User a = newUser("a@booktimer.com", "alpha", "동명이인");
        User b = newUser("b@booktimer.com", "bravo", "동명이인"); // 같은 닉네임
        publicBook(a, "알파의책");
        publicBook(b, "브라보의책");

        MvcResult resA = mockMvc.perform(get("/u/{loginId}", "alpha").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult resB = mockMvc.perform(get("/u/{loginId}", "bravo").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(booksInModel(resA)).extracting(Book::getTitle).containsExactly("알파의책");
        assertThat(booksInModel(resB)).extracting(Book::getTitle).containsExactly("브라보의책");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 본인이 자기 프로필을 봐도 PUBLIC만 (공개 미리보기)")
    void profile_self_onlyPublic() throws Exception {
        User me = newUser("me@booktimer.com", "myself", "나자신");
        publicBook(me, "내 공개책");
        privateBook(me, "내 비공개책");

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "myself").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(booksInModel(res)).extracting(Book::getTitle).containsExactly("내 공개책");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 팔로워/팔로잉 카운트와 팔로우 상태가 모델에 실린다")
    void profile_followInfo() throws Exception {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "owner", "주인");
        User third = newUser("third@booktimer.com", "third", "삼자");
        followService.follow(viewer, owner); // 내가 팔로우
        followService.follow(third, owner);  // 다른 사람도 팔로우 → 팔로워 2

        mockMvc.perform(get("/u/{loginId}", "owner").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("followerCount", 2L))
                .andExpect(model().attribute("following", true))
                .andExpect(model().attribute("self", false));
    }

    @Test
    @DisplayName("GET /u/{loginId}: 본인 프로필은 self=true (팔로우 버튼 없음)")
    void profile_self_noFollowButton() throws Exception {
        newUser("me@booktimer.com", "myself", "나자신");

        mockMvc.perform(get("/u/{loginId}", "myself").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("self", true))
                .andExpect(model().attribute("following", false));
    }

    @Test
    @DisplayName("GET /u/{loginId}?status=FINISHED: 해당 상태의 공개 책만 노출하고 shelfFilter를 싣는다")
    void profile_statusFilter_onlyMatching() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        publicBook(owner, "완독한 책", BookStatus.FINISHED);
        publicBook(owner, "읽는 중인 책", BookStatus.READING);

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "openking")
                        .param("status", "FINISHED").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfFilter", BookStatus.FINISHED))
                .andReturn();

        assertThat(booksInModel(res)).extracting(Book::getTitle).containsExactly("완독한 책");
    }

    @Test
    @DisplayName("GET /u/{loginId}: status 없으면 전체 공개 책 + shelfFilter=null·statuses 제공")
    void profile_noStatus_allBooks() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        publicBook(owner, "완독한 책", BookStatus.FINISHED);
        publicBook(owner, "읽는 중인 책", BookStatus.READING);

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfFilter", nullValue()))
                .andExpect(model().attributeExists("statuses"))
                .andReturn();

        assertThat(booksInModel(res)).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("완독한 책", "읽는 중인 책");
    }

    @Test
    @DisplayName("GET /u/{loginId}?status=BOGUS: 잘못된 상태면 전체(필터 없음)")
    void profile_invalidStatus_allBooks() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        publicBook(owner, "완독한 책", BookStatus.FINISHED);
        publicBook(owner, "읽는 중인 책", BookStatus.READING);

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "openking")
                        .param("status", "BOGUS").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfFilter", nullValue()))
                .andReturn();

        assertThat(booksInModel(res)).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("완독한 책", "읽는 중인 책");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 공개책에 구매링크가 있으면 그 책방에서 바로 구매하는 링크가 렌더된다(남의 책방 구매)")
    void profile_publicBookWithLink_rendersBuyLink() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        Book b = Book.register(owner, "구매가능책", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=q", BookStatus.READING);
        b.makePublic();
        Book saved = bookRepository.save(b);

        String html = mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 라벨 문자열이 아니라 "구매 경로(라우트 계약)"가 그 책방·그 책으로 렌더되는지를 본다
        assertThat(html).contains("/u/openking/books/" + saved.getId() + "/buy");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 본인 책방에서는 구매링크 있는 공개책이어도 구매 버튼이 렌더되지 않는다(내 책은 이미 내 것)")
    void profile_selfView_publicBookWithLink_noBuyButton() throws Exception {
        User me = newUser("me@booktimer.com", "myself", "나자신");
        Book b = Book.register(me, "내 구매가능책", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=q", BookStatus.READING);
        b.makePublic();
        Book saved = bookRepository.save(b);

        String html = mockMvc.perform(get("/u/{loginId}", "myself").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("self", true))
                .andReturn().getResponse().getContentAsString();

        // 본인 책방이면 같은 공개책+링크여도 구매 라우트가 렌더되지 않아야 한다(남의 책방에서만 뜸).
        assertThat(html).doesNotContain("/u/myself/books/" + saved.getId() + "/buy");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 구매링크 없는 공개책엔 구매 링크가 렌더되지 않는다(죽은 버튼 방지)")
    void profile_publicBookWithoutLink_noBuyLink() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        publicBook(owner, "링크없는공개책"); // purchaseLink 없음(수동 등록류)

        String html = mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 렌더된 실제 구매 앵커(해석된 href)가 없어야 한다 — 주석 속 리터럴 "{loginId}"가 아니라
        // openking으로 치환된 경로는 버튼이 실제 렌더될 때만 나타난다.
        assertThat(html).doesNotContain("/u/openking/books/");
    }

    @Test
    @DisplayName("비로그인 사용자는 프로필을 볼 수 없다 — 로그인으로 리다이렉트(로그인 한정 시작)")
    void profile_anonymous_redirectsToLogin() throws Exception {
        newUser("owner2@booktimer.com", "openking2", "공개왕2");

        mockMvc.perform(get("/u/{loginId}", "openking2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
