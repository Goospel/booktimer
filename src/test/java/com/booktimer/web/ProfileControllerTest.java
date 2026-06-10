package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.FollowService;
import com.booktimer.personality.ReadingPersonalityCache;
import com.booktimer.personality.ReadingPersonalityCacheRepository;
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

import java.time.Instant;
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
    @Autowired
    private ReadingPersonalityCacheRepository personalityCacheRepository;

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

    // --- 책BTI 책방 노출 (공개 책 기반 단일 캐시, 항상 공개 — opt-in 없음, 캐시 읽기 전용) ---

    /** 대상 유저의 책BTI <b>대표</b> 분석을 직접 적재한다(책방 조회는 대표를 읽기만 하므로 LLM 무관). */
    private void savePersonalityCache(User u, String narrative) {
        ReadingPersonalityCache cache = ReadingPersonalityCache.create(
                u, narrative, "태그", "sig", Instant.parse("2026-06-08T00:00:00Z"));
        cache.select(); // 책방엔 대표(selected) 서술만 실린다
        personalityCacheRepository.save(cache);
    }

    @Test
    @DisplayName("GET /u/{loginId}: 책BTI 캐시가 있으면 (항상 공개이므로) 성향 서술이 책방에 실린다")
    void profile_withPersonalityCache_exposesNarrative() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        savePersonalityCache(owner, "공개 책 기준, 이 사람은 경제서를 정독하는 독자다.");

        mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("personality", "공개 책 기준, 이 사람은 경제서를 정독하는 독자다."));
    }

    @Test
    @DisplayName("GET /u/{loginId}: 책BTI 캐시가 없으면(공개 완독 책 부족=콜드스타트) 성향 모델값이 비어 책BTI 탭은 안내문만 뜬다")
    void profile_noPersonalityCache_emptyNarrative() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        newUser("owner@booktimer.com", "openking", "공개왕");
        // 캐시 없음 — 방문자 조회는 읽기 전용이라 생성하지 않는다(LLM 미트리거)

        mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("personality", nullValue()));
    }

    // --- 공개 책장 탭 유지 (필터/탭 신호로 탭 기본값 보정 — 필터 클릭 후 책BTI로 튕기는 버그 차단) ---

    @Test
    @DisplayName("GET /u/{loginId}?status=READING: 상태 필터가 걸리면 성향이 있어도 공개 책장 탭이 활성(shelfActive=true) — 필터 클릭 후 책BTI로 안 튕김")
    void profile_statusFilter_activatesShelfTab() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        savePersonalityCache(owner, "성향 서술."); // 성향 있으면 원래 책BTI 먼저지만, 필터 걸면 책장 탭이어야
        publicBook(owner, "읽는 중인 책", BookStatus.READING);

        mockMvc.perform(get("/u/{loginId}", "openking")
                        .param("status", "READING").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfActive", true));
    }

    @Test
    @DisplayName("GET /u/{loginId}?tab=shelf: '전체' 칩(상태 없이 tab=shelf만)도 공개 책장 탭을 유지한다 — 전체로 돌아가도 안 튕김")
    void profile_tabShelf_activatesShelfTab() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        savePersonalityCache(owner, "성향 서술."); // 성향 있어도 tab=shelf면 책장 탭
        publicBook(owner, "아무책", BookStatus.READING);

        mockMvc.perform(get("/u/{loginId}", "openking")
                        .param("tab", "shelf").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfActive", true));
    }

    @Test
    @DisplayName("GET /u/{loginId}: HX-Request면 shelfPanel 프래그먼트만 반환(공개 책장 필터 부분 swap)")
    void profile_htmx_returnsFragment() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        publicBook(owner, "공개한 책", BookStatus.FINISHED);

        String html = mockMvc.perform(get("/u/{loginId}", "openking")
                        .param("status", "FINISHED").param("tab", "shelf")
                        .header("HX-Request", "true").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile :: shelfPanel"))
                .andExpect(model().attribute("shelfFilter", BookStatus.FINISHED))
                .andReturn().getResponse().getContentAsString();

        // 프래그먼트는 필터 칩(hx-get)·책 목록만 담고 전체 문서(<html>)는 아니어야 부분 swap이 성립
        assertThat(html).contains("hx-get").contains("공개한 책").doesNotContain("<html");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 일반 요청(HX-Request 없음)은 전체 profile 뷰를 반환한다(no-JS 폴백)")
    void profile_nonHtmx_returnsFullView() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        newUser("owner@booktimer.com", "openking", "공개왕");

        mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"));
    }

    // --- 책BTI 태그 책방 헤더 노출 (#281 결정적 태거 ReadingTagger·ReadingTribe 출력 노출 — 캐시·LLM 무관) ---

    private void publicFinishedWithCategory(User owner, String title, String category) {
        Book b = Book.register(owner, title, null, null, null, null, null,
                category, null, BookStatus.FINISHED);
        b.makePublic();
        bookRepository.save(b);
    }

    private void privateFinishedWithCategory(User owner, String title, String category) {
        Book b = Book.register(owner, title, null, null, null, null, null,
                category, null, BookStatus.FINISHED);
        bookRepository.save(b);
    }

    @SuppressWarnings("unchecked")
    private List<String> tagsInModel(MvcResult res) {
        return (List<String>) res.getModelAndView().getModel().get("personalityTags");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 공개 완독 책의 종족이 책방 헤더 태그(personalityTags)로 실린다 — 캐시·LLM 무관")
    void profile_publicFinished_populatesPersonalityTags() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        // 알라딘 대분류 "소설/시/희곡" → STORY(이야기파). 결정적 태거가 1위 종족을 항상 출력.
        publicFinishedWithCategory(owner, "소설1", "국내도서>소설/시/희곡>한국소설");

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(tagsInModel(res)).contains("이야기파");
    }

    @Test
    @DisplayName("GET /u/{loginId}: PRIVATE 완독 책의 장르는 헤더 태그에 새지 않는다(공개 책 기반 입력 — 누출 차단)")
    void profile_privateFinished_doesNotLeakIntoPersonalityTags() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        publicFinishedWithCategory(owner, "소설1", "국내도서>소설/시/희곡>한국소설"); // STORY 공개
        // 비공개 경제경영(=PRACTICAL=실용파) — 헤더 태그에 절대 노출되면 안 됨
        privateFinishedWithCategory(owner, "비밀경제서", "국내도서>경제경영>경영전략");

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(tagsInModel(res)).doesNotContain("실용파");
    }

    @Test
    @DisplayName("GET /u/{loginId}: 공개 완독 0권이면 personalityTags는 빈 목록(헤더 칩 행 숨김)")
    void profile_noPublicFinished_emptyPersonalityTags() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        newUser("owner@booktimer.com", "openking", "공개왕");
        // 책 없음 → 태거 콜드스타트 → 빈 목록

        MvcResult res = mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(tagsInModel(res)).isEmpty();
    }

    @Test
    @DisplayName("GET /u/{loginId}: 파라미터 없는 첫 진입은 shelfActive=false (성향 있으면 책BTI 탭 먼저 — 의도된 흐름 유지)")
    void profile_firstVisit_shelfInactive() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        savePersonalityCache(owner, "성향 서술.");
        publicBook(owner, "아무책", BookStatus.READING);

        mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfActive", false));
    }
}
