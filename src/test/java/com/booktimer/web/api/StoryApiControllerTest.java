package com.booktimer.web.api;

import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.security.RateLimitService;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoryApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER, today());
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Book publicBookOf(User owner, String title) {
        Book book = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        return bookRepository.save(book);
    }

    @Test
    @DisplayName("POST /api/stories 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void create_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/stories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\",\"bookId\":1}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/stories CSRF 없으면 403")
    void create_withoutCsrf_returns403() throws Exception {
        register("story-csrf@booktimer.com", "storycsrf", "작성자");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-csrf@booktimer.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\",\"bookId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/stories 인증+csrf → 200, 남긴 글 반환")
    void create_authenticated_returnsEntry() throws Exception {
        User me = register("story-author@booktimer.com", "storyauthor", "작성자");
        Book book = publicBookOf(me, "여백이 열린 책");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-author@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"인상 깊은 문장\",\"bookId\":" + book.getId() + ",\"bgCode\":\"night\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("인상 깊은 문장"))
                .andExpect(jsonPath("$.bgCode").value("night"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/stories bookId 없음 → 400 (여백은 책에 귀속)")
    void create_withoutBookId_returns400() throws Exception {
        register("story-nobook@booktimer.com", "storynobook", "작성자");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-nobook@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"책 없는 문장\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/stories 남의 책에 글 남기기 → 404 (IDOR — 존재 누설 금지)")
    void create_othersBook_returns404() throws Exception {
        User owner = register("story-owner@booktimer.com", "storyowner", "주인");
        register("story-intruder@booktimer.com", "storyintruder", "침입자");
        Book book = publicBookOf(owner, "남의 책");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-intruder@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"남의 여백에 낙서\",\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/stories 도메인 검증 실패(팔레트 밖 bgCode) → 400")
    void create_invalidBgCode_returns400() throws Exception {
        User me = register("story-bad@booktimer.com", "storybad", "작성자");
        Book book = publicBookOf(me, "책");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-bad@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\",\"bookId\":" + book.getId() + ",\"bgCode\":\"#ff0000\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/stories/of/{loginId}?bookId= (책 하나의 여백) ---

    @Test
    @DisplayName("GET /api/stories/of 차단 관계 → 404 (존재 누설 금지)")
    void marginOf_blocked_returns404() throws Exception {
        register("of-viewer@booktimer.com", "ofviewer", "열람자");
        User target = register("of-target@booktimer.com", "oftarget", "대상");
        User viewer = userRepository.findByEmail("of-viewer@booktimer.com").orElseThrow();
        Book book = publicBookOf(target, "가려질 책");
        blockRepository.save(Block.of(target, viewer));

        mockMvc.perform(get("/api/stories/of/oftarget")
                        .param("bookId", String.valueOf(book.getId()))
                        .with(user("of-viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/stories/of 남의 책 id를 다른 핸들에 끼워 넣으면 → 404 (IDOR)")
    void marginOf_bookOfAnotherOwner_returns404() throws Exception {
        register("idor-viewer@booktimer.com", "idorviewer", "열람자");
        User target = register("idor-target@booktimer.com", "idortarget", "대상");
        User stranger = register("idor-other@booktimer.com", "idorother", "제3자");
        Book strangersBook = publicBookOf(stranger, "제3자의 책");
        followRepository.save(Follow.of(
                userRepository.findByEmail("idor-viewer@booktimer.com").orElseThrow(), target));

        mockMvc.perform(get("/api/stories/of/idortarget")
                        .param("bookId", String.valueOf(strangersBook.getId()))
                        .with(user("idor-viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/stories/of 상대의 PRIVATE 책 → 404 (비공개 책의 글은 새지 않는다)")
    void marginOf_privateBook_returns404() throws Exception {
        User viewer = register("pv-viewer@booktimer.com", "pvviewer", "열람자");
        User target = register("pv-target@booktimer.com", "pvtarget", "대상");
        followRepository.save(Follow.of(viewer, target));
        Book secret = bookRepository.save(
                Book.register(target, "비공개 책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(get("/api/stories/of/pvtarget")
                        .param("bookId", String.valueOf(secret.getId()))
                        .with(user("pv-viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/stories/of 비팔로워 → 200 + 책 라벨은 주되 entries는 빈 배열")
    void marginOf_nonFollower_returnsEmptyEntries() throws Exception {
        register("nf-viewer@booktimer.com", "nfviewer", "열람자");
        User target = register("nf-target@booktimer.com", "nftarget", "대상");
        Book book = publicBookOf(target, "공개 책");
        storyRepository.save(Story.of(target, "비팔로워에겐 안 보일 문장", book, null));

        mockMvc.perform(get("/api/stories/of/nftarget")
                        .param("bookId", String.valueOf(book.getId()))
                        .with(user("nf-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.book.title").value("공개 책"))
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.self").value(false))
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    @Test
    @DisplayName("GET /api/stories/of 팔로워 → 그 책 여백의 글 목록(최신순)")
    void marginOf_follower_returnsEntries() throws Exception {
        User viewer = register("fw-viewer@booktimer.com", "fwviewer", "열람자");
        User target = register("fw-target@booktimer.com", "fwtarget", "대상");
        followRepository.save(Follow.of(viewer, target));
        Book book = publicBookOf(target, "공개 책");
        storyRepository.save(Story.of(target, "팔로워에겐 보일 문장", book, "sea"));

        mockMvc.perform(get("/api/stories/of/fwtarget")
                        .param("bookId", String.valueOf(book.getId()))
                        .with(user("fw-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerNickname").value("대상"))
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.entries[0].text").value("팔로워에겐 보일 문장"))
                .andExpect(jsonPath("$.entries[0].bgCode").value("sea"));
    }

    // ── 비공개 책 여백 (2026-08-16 결정 2) — 주인만 열린다 ─────────────────────
    // 실 H2 통합으로 간다: 게이트가 「책 가시성 × 소유자 판정」의 조합이라 mock으로는 쿼리 필터와
    // 소유 조건이 진짜로 걸리는지 검증되지 않는다(T-023 계열).

    private Book privateBookOf(User owner, String title) {
        return bookRepository.save(
                Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    /** §5-1 ⓔ — 주인은 자기 비공개 책의 여백을 읽고 쓴다. */
    @Test
    @DisplayName("비공개 책: 주인은 글을 남기고(200) 자기 여백을 읽는다(self:true, book.isPublic:false)")
    void privateBook_owner_writesAndReads() throws Exception {
        User me = register("pb-owner@booktimer.com", "pbowner", "주인");
        Book secret = privateBookOf(me, "내 비공개 책");

        mockMvc.perform(post("/api/stories")
                        .with(user("pb-owner@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"나만 보는 메모\",\"bookId\":" + secret.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("나만 보는 메모"));

        mockMvc.perform(get("/api/stories/of/pbowner")
                        .param("bookId", String.valueOf(secret.getId()))
                        .with(user("pb-owner@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.self").value(true))
                .andExpect(jsonPath("$.book.title").value("내 비공개 책"))
                .andExpect(jsonPath("$.book.isPublic").value(false))
                .andExpect(jsonPath("$.entries[0].text").value("나만 보는 메모"));
    }

    /** §5-1 ⓔ 짝 — 공개 책이면 같은 필드가 true다(직렬화 ⓖ의 양성 대조군). */
    @Test
    @DisplayName("공개 책: 여백 응답의 book.isPublic은 true — 캡션이 「팔로워에게 보여요」로 갈리는 근거")
    void marginOf_publicBook_isPublicTrue() throws Exception {
        User me = register("pb-pub@booktimer.com", "pbpub", "주인");
        Book open = publicBookOf(me, "내 공개 책");

        mockMvc.perform(get("/api/stories/of/pbpub")
                        .param("bookId", String.valueOf(open.getId()))
                        .with(user("pb-pub@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.book.isPublic").value(true));
    }

    /** §5-1 ⓑ — 완화는 <b>읽기 게이트</b>만 건드렸다. 남이 내 비공개 책에 쓰는 길은 여전히 없다. */
    @Test
    @DisplayName("비공개 책: 남이 글을 남기려 하면 404 — 소유 게이트는 완화 대상이 아니다(IDOR)")
    void privateBook_stranger_cannotWrite() throws Exception {
        User owner = register("pb-victim@booktimer.com", "pbvictim", "주인");
        register("pb-intruder@booktimer.com", "pbintruder", "침입자");
        Book secret = privateBookOf(owner, "남의 비공개 책");

        mockMvc.perform(post("/api/stories")
                        .with(user("pb-intruder@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"남의 여백에 낙서\",\"bookId\":" + secret.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    /** §5-1 ⓐ 실데이터판 — 글이 실제로 존재하는 비공개 책이어도 팔로워에겐 404다(빈 entries 200 아님). */
    @Test
    @DisplayName("비공개 책: 글이 있어도 팔로워에게는 404 — 존재조차 노출하지 않는다")
    void privateBook_followerWithEntries_returns404() throws Exception {
        User viewer = register("pb-fviewer@booktimer.com", "pbfviewer", "팔로워");
        User owner = register("pb-fowner@booktimer.com", "pbfowner", "주인");
        followRepository.save(Follow.of(viewer, owner));
        Book secret = privateBookOf(owner, "글 있는 비공개 책");
        storyRepository.save(Story.of(owner, "새면 안 되는 메모", secret, null));

        mockMvc.perform(get("/api/stories/of/pbfowner")
                        .param("bookId", String.valueOf(secret.getId()))
                        .with(user("pb-fviewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    /**
     * §5-1 ⓓ — 가시성 <b>전환</b>. 글에 자체 공개 필드가 없어 동기화 코드가 0줄이므로, 「전환하면
     * 자동으로 사라진다」는 것 자체가 검증 대상이다. 여백 게이트와 소식 피드 두 경로를 한 흐름에서 본다.
     */
    @Test
    @DisplayName("PUBLIC→PRIVATE 전환: 같은 팔로워가 보던 여백은 404, 소식에서도 사라진다 (동기화 코드 0줄)")
    void publicToPrivate_hidesMarginAndFeedFromFollower() throws Exception {
        User viewer = register("tr-viewer@booktimer.com", "trviewer", "팔로워");
        User owner = register("tr-owner@booktimer.com", "trowner", "주인");
        followRepository.save(Follow.of(viewer, owner));
        Book book = publicBookOf(owner, "나중에 비공개가 될 책");
        storyRepository.save(Story.of(owner, "공개일 때 남긴 글", book, null));

        mockMvc.perform(get("/api/stories/of/trowner")
                        .param("bookId", String.valueOf(book.getId()))
                        .with(user("tr-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].text").value("공개일 때 남긴 글"));
        mockMvc.perform(get("/api/home-feed").with(user("tr-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social[0].type").value("STORY"));

        book.makePrivate();
        bookRepository.save(book);

        mockMvc.perform(get("/api/stories/of/trowner")
                        .param("bookId", String.valueOf(book.getId()))
                        .with(user("tr-viewer@booktimer.com")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/home-feed").with(user("tr-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.social").isEmpty());
    }

    @Test
    @DisplayName("DELETE /api/stories/{id} 타인 글 → 404 (IDOR)")
    void delete_othersStory_returns404() throws Exception {
        User author = register("del-author@booktimer.com", "delauthor", "작성자");
        register("del-actor@booktimer.com", "delactor", "삭제시도자");
        Story story = storyRepository.save(Story.of(author, "남의 문장", publicBookOf(author, "책"), null));

        mockMvc.perform(delete("/api/stories/" + story.getId())
                        .with(user("del-actor@booktimer.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── 좋아요 (POST/DELETE /api/stories/{id}/like) ──
    // 게이트 판정 자체는 StoryServiceTest가 전수로 계측한다. 여기서 잡는 것은 <b>배선</b>이다 —
    // 새 경로가 default-deny·CSRF 안에 들어왔는가, 왕복이 JSON으로 직렬화되는가, 게이트가 실제로
    // 호출되는가. 셋 다 서비스 단위테스트로는 원리상 안 보인다.

    private Story storyOf(User author, Book book, String text) {
        return storyRepository.save(Story.of(author, text, book, null));
    }

    private void follow(User follower, User followee) {
        followRepository.save(Follow.of(follower, followee));
    }

    @Test
    @DisplayName("POST /api/stories/{id}/like 미인증 → 302 로그인 리다이렉트 (새 경로도 기본 잠김)")
    void like_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/stories/1/like").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/stories/{id}/like CSRF 없으면 403")
    void like_withoutCsrf_returns403() throws Exception {
        register("like-csrf@booktimer.com", "likecsrf", "누르는이");

        mockMvc.perform(post("/api/stories/1/like").with(user("like-csrf@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("좋아요 왕복 — 누르면 1·liked, 목록에 실리고, 취소하면 0으로 돌아온다")
    void like_thenUnlike_roundTrip() throws Exception {
        User author = register("like-author@booktimer.com", "likeauthor", "글쓴이");
        User fan = register("like-fan@booktimer.com", "likefan", "독자");
        Book book = publicBookOf(author, "좋아요가 달릴 책");
        Story story = storyOf(author, book, "누를 만한 문장");
        follow(fan, author);

        mockMvc.perform(post("/api/stories/" + story.getId() + "/like")
                        .with(user("like-fan@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").value(true));

        // 목록에도 실린다 — 카드가 하트를 채우는 근거
        mockMvc.perform(get("/api/stories/of/likeauthor").param("bookId", String.valueOf(book.getId()))
                        .with(user("like-fan@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].likeCount").value(1))
                .andExpect(jsonPath("$.entries[0].liked").value(true));

        mockMvc.perform(delete("/api/stories/" + story.getId() + "/like")
                        .with(user("like-fan@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    @DisplayName("POST .../like 비팔로워 → 404 (안 보이는 글에 눌러 보고 존재를 알아낼 수 없다)")
    void like_nonFollower_returns404() throws Exception {
        User author = register("like-closed@booktimer.com", "likeclosed", "글쓴이");
        register("like-stranger@booktimer.com", "likestranger", "남");
        Story story = storyOf(author, publicBookOf(author, "공개 책"), "남의 문장");

        mockMvc.perform(post("/api/stories/" + story.getId() + "/like")
                        .with(user("like-stranger@booktimer.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST .../like 내 글 → 404 (여백은 내 노트라 자기 좋아요는 없다)")
    void like_ownStory_returns404() throws Exception {
        User me = register("like-self@booktimer.com", "likeself", "나");
        Story story = storyOf(me, publicBookOf(me, "내 책"), "내 문장");

        mockMvc.perform(post("/api/stories/" + story.getId() + "/like")
                        .with(user("like-self@booktimer.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
