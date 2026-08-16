package com.booktimer.web.api;

import com.booktimer.block.BlockService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.book.Yes24LinkBuilder;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.personality.ReadingPersonalityCache;
import com.booktimer.personality.ReadingPersonalityCacheRepository;
import com.booktimer.report.ReportReason;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/profile · /api/profile/books · /api/profile/personality-tag 컨트롤러 통합 테스트.
 * 선별 SPA 단계 2d — ProfileApiController 신설 경계 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BlockService blockService;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private StoryRepository storyRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private ReadingPersonalityCacheRepository personalityCacheRepository;

    @Autowired
    private Clock clock;

    @MockitoBean
    private CoupangLinkBuilder coupangLinkBuilder;

    @MockitoBean
    private Yes24LinkBuilder yes24LinkBuilder;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private User registerAdmin(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.ADMIN, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private void publicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.makePublic();
        bookRepository.save(b);
    }

    private void publicBook(User owner, String title, BookStatus status) {
        Book b = Book.register(owner, title, null, null, null, null, null, status);
        b.makePublic();
        bookRepository.save(b);
    }

    /** 공개 책 하나를 돌려준다 — 여백에 글을 달려면 책 인스턴스가 필요하다. */
    private Book publicBookOf(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.makePublic();
        return bookRepository.save(b);
    }

    /** 그 책의 여백에 글을 남기고 생성 시각을 원하는 값으로 되돌린다(@CreatedDate 우회). */
    private void storyAt(User owner, Book book, String text, Instant createdAt) {
        Story story = storyRepository.save(Story.of(owner, text, book, null));
        entityManager.createQuery("update Story s set s.createdAt = :t where s.id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", story.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private void privateBook(User owner, String title) {
        bookRepository.save(Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    private void publicFinishedWithCategory(User owner, String title, String category) {
        Book b = Book.register(owner, title, null, null, null, null, null, category, null, BookStatus.FINISHED);
        b.makePublic();
        bookRepository.save(b);
    }

    private void savePersonalityCache(User u, String narrative) {
        ReadingPersonalityCache cache = ReadingPersonalityCache.create(
                u, narrative, "태그", "sig", Instant.parse("2026-06-08T00:00:00Z"));
        cache.select();
        personalityCacheRepository.save(cache);
    }

    // ── 1. 미인증 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/profile 미인증 → 302 /login")
    void profile_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/profile").param("loginId", "anyone"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/profile/books 미인증 → 302 /login")
    void books_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/profile/books").param("loginId", "anyone"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/profile/personality-tag 미인증 → 302 /login")
    void personalityTag_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/profile/personality-tag").param("loginId", "anyone").param("tag", "이야기파"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── 2. 응답 구조 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/profile → loginId·nickname·followerCount·self·personalityTags·books·coupangEnabled 존재")
    void profile_authenticated_returnsExpectedFields() throws Exception {
        register("pa-viewer@booktimer.com", "paviewerid", "뷰어");
        User owner = register("pa-owner@booktimer.com", "paownerid", "주인");
        publicBook(owner, "공개책");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "paownerid")
                        .with(user("pa-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("paownerid"))
                .andExpect(jsonPath("$.nickname").value("주인"))
                .andExpect(jsonPath("$.followerCount").isNumber())
                .andExpect(jsonPath("$.self").value(false))
                .andExpect(jsonPath("$.personalityTags").isArray())
                .andExpect(jsonPath("$.books").isArray())
                .andExpect(jsonPath("$.coupangEnabled").isBoolean())
                .andExpect(jsonPath("$.yes24Enabled").isBoolean());
    }

    @Test
    @DisplayName("GET /api/profile 본인 조회 → self:true")
    void profile_self_returnsTrue() throws Exception {
        register("pa-self@booktimer.com", "paselfid", "자신");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "paselfid")
                        .with(user("pa-self@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.self").value(true));
    }

    @Test
    @DisplayName("GET /api/profile: 대상이 프로필 작가를 선택했으면 profileCharacterCode를 응답에 싣는다")
    void profile_withProfileCharacter_includesCode() throws Exception {
        User u = register("pa-pc@booktimer.com", "papcid", "프사주인");
        u.selectProfileCharacter("han_gang"); // 엔티티 직접(보유검증 우회) — 노출 경로만 검증
        userRepository.save(u);

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "papcid")
                        .with(user("pa-pc@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCharacterCode").value("han_gang"));
    }

    // ── 3. PRIVATE 책 비노출 (최우선 누수 가드) ──────────────────────────

    @Test
    @DisplayName("GET /api/profile: PUBLIC 책만 응답에 포함, PRIVATE 책은 제외")
    void profile_onlyPublicBooksInResponse() throws Exception {
        register("pa-vw2@booktimer.com", "pavw2id", "뷰어2");
        User owner = register("pa-ow2@booktimer.com", "paow2id", "주인2");
        publicBook(owner, "공개책2");
        privateBook(owner, "비공개책2");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "paow2id")
                        .with(user("pa-vw2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].title").value("공개책2"))
                .andExpect(content().string(not(containsString("비공개책2"))));
    }

    @Test
    @DisplayName("GET /api/profile: 남의 독서 잔디(graph)는 응답에 없다 — 현재 미열람 기능, 재유입 가드")
    void profile_doesNotExposeOthersGrass() throws Exception {
        // 남의 잔디 열람은 미구현(라우트·직렬화 없음). 죽은 publicContributionGraph 경로를 제거한 뒤,
        // 누군가 graph를 ProfileResponse에 다시 끼워 넣어 남의 잔디가 새지 않도록 불변식으로 가드한다.
        register("pa-grass-vw@booktimer.com", "pagrassvw", "뷰어");
        User owner = register("pa-grass-ow@booktimer.com", "pagrassow", "주인");
        publicBook(owner, "공개책G");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "pagrassow")
                        .with(user("pa-grass-vw@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph").doesNotExist())
                .andExpect(content().string(not(containsString("\"graph\""))));
    }

    @Test
    @DisplayName("GET /api/profile/books: PUBLIC 책만, PRIVATE 책 제외")
    void books_onlyPublicBooksInResponse() throws Exception {
        register("pa-vw3@booktimer.com", "pavw3id", "뷰어3");
        User owner = register("pa-ow3@booktimer.com", "paow3id", "주인3");
        publicBook(owner, "공개책3");
        privateBook(owner, "비공개책3");

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "paow3id")
                        .with(user("pa-vw3@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(content().string(not(containsString("비공개책3"))));
    }

    // ── 4. 소유자 예외 없음 (본인이 봐도 PUBLIC만) ──────────────────────

    @Test
    @DisplayName("GET /api/profile 본인이 봐도 PRIVATE 책은 제외 (소유자 예외 없음)")
    void profile_self_privateBookExcluded() throws Exception {
        User me = register("pa-me@booktimer.com", "pameid", "나");
        publicBook(me, "내 공개책");
        privateBook(me, "내 비공개책");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "pameid")
                        .with(user("pa-me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(content().string(not(containsString("내 비공개책"))));
    }

    // ── 5. 상태필터 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/profile/books?status=READING → READING 책만")
    void books_statusFilter_reading() throws Exception {
        register("pa-vw4@booktimer.com", "pavw4id", "뷰어4");
        User owner = register("pa-ow4@booktimer.com", "paow4id", "주인4");
        publicBook(owner, "읽는중책", BookStatus.READING);
        publicBook(owner, "완독책", BookStatus.FINISHED);

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "paow4id")
                        .param("status", "READING")
                        .with(user("pa-vw4@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].title").value("읽는중책"));
    }

    @Test
    @DisplayName("GET /api/profile/books?status=garbage → 전체(관대 파싱)")
    void books_garbageStatus_returnsAll() throws Exception {
        register("pa-vw5@booktimer.com", "pavw5id", "뷰어5");
        User owner = register("pa-ow5@booktimer.com", "paow5id", "주인5");
        publicBook(owner, "읽는중책5", BookStatus.READING);
        publicBook(owner, "완독책5", BookStatus.FINISHED);

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "paow5id")
                        .param("status", "BOGUS_STATUS")
                        .with(user("pa-vw5@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/profile/books status 없음 → 전체")
    void books_noStatus_returnsAll() throws Exception {
        register("pa-vw6@booktimer.com", "pavw6id", "뷰어6");
        User owner = register("pa-ow6@booktimer.com", "paow6id", "주인6");
        publicBook(owner, "읽는중책6", BookStatus.READING);
        publicBook(owner, "완독책6", BookStatus.FINISHED);

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "paow6id")
                        .with(user("pa-vw6@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(2)));
    }

    // ── 5b. 정렬 — 기본 이름순 + 완독 시각 정렬 ──────────────────────────

    private void publicFinishedAt(User owner, String title, Instant finishedAt) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.changeStatus(BookStatus.FINISHED, finishedAt);
        b.makePublic();
        bookRepository.save(b);
    }

    @Test
    @DisplayName("GET /api/profile/books 기본 정렬은 이름순(제목 오름차순) — 상태 무관")
    void books_defaultSort_titleAsc() throws Exception {
        register("pa-svw@booktimer.com", "pasvwid", "정렬뷰어");
        User owner = register("pa-sow@booktimer.com", "pasowid", "정렬주인");
        publicBook(owner, "다책", BookStatus.READING);
        publicBook(owner, "가책", BookStatus.FINISHED);
        publicBook(owner, "나책", BookStatus.WANT_TO_READ);

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "pasowid")
                        .with(user("pa-svw@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("가책"))
                .andExpect(jsonPath("$.books[1].title").value("나책"))
                .andExpect(jsonPath("$.books[2].title").value("다책"));
    }

    @Test
    @DisplayName("GET /api/profile 초기 전체 목록도 이름순")
    void profile_booksSortedByTitle() throws Exception {
        register("pa-svw2@booktimer.com", "pasvw2id", "정렬뷰어2");
        User owner = register("pa-sow2@booktimer.com", "pasow2id", "정렬주인2");
        publicBook(owner, "나책2", BookStatus.READING);
        publicBook(owner, "가책2", BookStatus.FINISHED);

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "pasow2id")
                        .with(user("pa-svw2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("가책2"))
                .andExpect(jsonPath("$.books[1].title").value("나책2"));
    }

    @Test
    @DisplayName("GET /api/profile/books?status=FINISHED&sort=finished_desc → 완독 최신순")
    void books_sortFinishedDesc() throws Exception {
        register("pa-svw3@booktimer.com", "pasvw3id", "정렬뷰어3");
        User owner = register("pa-sow3@booktimer.com", "pasow3id", "정렬주인3");
        publicFinishedAt(owner, "가책3", Instant.parse("2026-06-01T00:00:00Z"));
        publicFinishedAt(owner, "나책3", Instant.parse("2026-06-03T00:00:00Z"));
        publicFinishedAt(owner, "다책3", Instant.parse("2026-06-02T00:00:00Z"));

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "pasow3id")
                        .param("status", "FINISHED")
                        .param("sort", "finished_desc")
                        .with(user("pa-svw3@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("나책3"))
                .andExpect(jsonPath("$.books[1].title").value("다책3"))
                .andExpect(jsonPath("$.books[2].title").value("가책3"));
    }

    @Test
    @DisplayName("GET /api/profile/books?status=FINISHED&sort=finished_asc → 완독 오래된순")
    void books_sortFinishedAsc() throws Exception {
        register("pa-svw4@booktimer.com", "pasvw4id", "정렬뷰어4");
        User owner = register("pa-sow4@booktimer.com", "pasow4id", "정렬주인4");
        publicFinishedAt(owner, "가책4", Instant.parse("2026-06-02T00:00:00Z"));
        publicFinishedAt(owner, "나책4", Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "pasow4id")
                        .param("status", "FINISHED")
                        .param("sort", "finished_asc")
                        .with(user("pa-svw4@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("나책4"))
                .andExpect(jsonPath("$.books[1].title").value("가책4"));
    }

    @Test
    @DisplayName("완독 시각 없는 완독 책(백필 전 레거시 형태)은 완독 정렬에서 뒤로 간다 — null-state 경계(N-055)")
    void books_sortFinished_nullFinishedAtGoesLast() throws Exception {
        register("pa-svw5@booktimer.com", "pasvw5id", "정렬뷰어5");
        User owner = register("pa-sow5@booktimer.com", "pasow5id", "정렬주인5");
        publicBook(owner, "시각없는완독책", BookStatus.FINISHED); // 엔티티 단독 register → finishedAt null
        publicFinishedAt(owner, "시각있는완독책", Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "pasow5id")
                        .param("status", "FINISHED")
                        .param("sort", "finished_desc")
                        .with(user("pa-svw5@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("시각있는완독책"))
                .andExpect(jsonPath("$.books[1].title").value("시각없는완독책"));
    }

    @Test
    @DisplayName("GET /api/profile/books?sort=garbage → 이름순(관대 파싱 — status와 동일 정신)")
    void books_garbageSort_fallsBackToTitle() throws Exception {
        register("pa-svw6@booktimer.com", "pasvw6id", "정렬뷰어6");
        User owner = register("pa-sow6@booktimer.com", "pasow6id", "정렬주인6");
        publicFinishedAt(owner, "나책6", Instant.parse("2026-06-03T00:00:00Z"));
        publicFinishedAt(owner, "가책6", Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "pasow6id")
                        .param("status", "FINISHED")
                        .param("sort", "BOGUS_SORT")
                        .with(user("pa-svw6@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("가책6"))
                .andExpect(jsonPath("$.books[1].title").value("나책6"));
    }

    // ── 6. 차단 → 404 (세 API 모두) ────────────────────────────────────

    @Test
    @DisplayName("GET /api/profile 차단 관계(양방향) → 404")
    void profile_blocked_returns404() throws Exception {
        User viewer = register("pa-bvw@booktimer.com", "pabvwid", "차단뷰어");
        User owner = register("pa-bow@booktimer.com", "pabowid", "차단당한자");
        blockService.block(owner, viewer); // 상대가 나를 차단 → 대칭 404

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "pabowid")
                        .with(user("pa-bvw@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/profile/books 차단 관계 → 404")
    void books_blocked_returns404() throws Exception {
        User viewer = register("pa-bvw2@booktimer.com", "pabvw2id", "차단뷰어2");
        User owner = register("pa-bow2@booktimer.com", "pabow2id", "차단당한자2");
        blockService.block(viewer, owner); // 내가 차단

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "pabow2id")
                        .with(user("pa-bvw2@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/profile/personality-tag 차단 관계 → 404")
    void personalityTag_blocked_returns404() throws Exception {
        User viewer = register("pa-bvw3@booktimer.com", "pabvw3id", "차단뷰어3");
        User owner = register("pa-bow3@booktimer.com", "pabow3id", "차단당한자3");
        blockService.block(viewer, owner);

        mockMvc.perform(get("/api/profile/personality-tag")
                        .param("loginId", "pabow3id")
                        .param("tag", "이야기파")
                        .with(user("pa-bvw3@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    // ── 7. ADMIN → 404 ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/profile ADMIN 대상 → 404")
    void profile_admin_returns404() throws Exception {
        register("pa-uvw@booktimer.com", "pauvwid", "유저뷰어");
        registerAdmin("pa-adm@booktimer.com", "paadmid", "운영자");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "paadmid")
                        .with(user("pa-uvw@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/profile/books ADMIN 대상 → 404")
    void books_admin_returns404() throws Exception {
        register("pa-uvw2@booktimer.com", "pauvw2id", "유저뷰어2");
        registerAdmin("pa-adm2@booktimer.com", "paadm2id", "운영자2");

        mockMvc.perform(get("/api/profile/books")
                        .param("loginId", "paadm2id")
                        .with(user("pa-uvw2@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/profile/personality-tag ADMIN 대상 → 404")
    void personalityTag_admin_returns404() throws Exception {
        register("pa-uvw3@booktimer.com", "pauvw3id", "유저뷰어3");
        registerAdmin("pa-adm3@booktimer.com", "paadm3id", "운영자3");

        mockMvc.perform(get("/api/profile/personality-tag")
                        .param("loginId", "paadm3id")
                        .param("tag", "이야기파")
                        .with(user("pa-uvw3@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    // ── 8. 미존재 loginId → 404 ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/profile 없는 loginId → 404")
    void profile_nonexistentLoginId_returns404() throws Exception {
        register("pa-nf@booktimer.com", "panfid", "존재확인자");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "zz_nonexistent_pa_xyz_9999")
                        .with(user("pa-nf@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    // ── 9. 태그 드릴다운 PUBLIC∩FINISHED ────────────────────────────────

    @Test
    @DisplayName("GET /api/profile/personality-tag → 그 종족 PUBLIC+FINISHED 책만")
    void personalityTag_returnsOnlyPublicFinished() throws Exception {
        register("pa-tvw@booktimer.com", "patvwid", "태그뷰어");
        User owner = register("pa-tow@booktimer.com", "patowid", "태그주인");
        publicFinishedWithCategory(owner, "소설1", "국내도서>소설/시/희곡>한국소설");
        publicFinishedWithCategory(owner, "역사1", "국내도서>역사>한국사");

        mockMvc.perform(get("/api/profile/personality-tag")
                        .param("loginId", "patowid")
                        .param("tag", "이야기파")
                        .with(user("pa-tvw@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].title").value("소설1"));
    }

    @Test
    @DisplayName("GET /api/profile/personality-tag 알 수 없는 태그 → 빈 배열 + 200")
    void personalityTag_unknownTag_returnsEmpty() throws Exception {
        register("pa-tvw2@booktimer.com", "patvw2id", "태그뷰어2");
        User owner = register("pa-tow2@booktimer.com", "patow2id", "태그주인2");
        publicFinishedWithCategory(owner, "소설2", "국내도서>소설/시/희곡>한국소설");

        mockMvc.perform(get("/api/profile/personality-tag")
                        .param("loginId", "patow2id")
                        .param("tag", "외길형") // non-clickable 태그 → booksForTag = 빈 목록
                        .with(user("pa-tvw2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books").isArray());
    }

    // ── 10. N+1 회피 (seconds 정확성) ───────────────────────────────────

    @Test
    @DisplayName("GET /api/profile books[*].seconds 필드 존재 (N+1 없이 일괄 Map)")
    void profile_booksHaveSecondsField() throws Exception {
        register("pa-nvw@booktimer.com", "panvwid", "N+1뷰어");
        User owner = register("pa-now@booktimer.com", "panowid", "N+1주인");
        publicBook(owner, "타이머책");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "panowid")
                        .with(user("pa-nvw@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].seconds").isNumber());
    }

    // ── 11. coupangEnabled 실값 + Book DTO 화이트리스트 ──────────────────

    @Test
    @DisplayName("GET /api/profile coupangEnabled=true 시 응답에 true 반영 (false 하드코딩 회귀 방지)")
    void profile_coupangEnabled_true_reflectedInResponse() throws Exception {
        when(coupangLinkBuilder.isEnabled()).thenReturn(true);
        register("pa-cvw@booktimer.com", "pacvwid", "쿠팡뷰어");
        User owner = register("pa-cow@booktimer.com", "pacowid", "쿠팡주인");
        publicBook(owner, "쿠팡책");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "pacowid")
                        .with(user("pa-cvw@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupangEnabled").value(true));
    }

    @Test
    @DisplayName("GET /api/profile coupangEnabled=false 시 응답에 false 반영")
    void profile_coupangEnabled_false_reflectedInResponse() throws Exception {
        when(coupangLinkBuilder.isEnabled()).thenReturn(false);
        register("pa-cvw2@booktimer.com", "pacvw2id", "쿠팡뷰어2");
        User owner = register("pa-cow2@booktimer.com", "pacow2id", "쿠팡주인2");
        publicBook(owner, "쿠팡책2");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "pacow2id")
                        .with(user("pa-cvw2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupangEnabled").value(false));
    }

    @Test
    @DisplayName("GET /api/profile yes24Enabled=true 시 응답에 true 반영 (false 하드코딩 회귀 방지)")
    void profile_yes24Enabled_true_reflectedInResponse() throws Exception {
        when(yes24LinkBuilder.isEnabled()).thenReturn(true);
        register("pa-yvw@booktimer.com", "payvwid", "예스뷰어");
        User owner = register("pa-yow@booktimer.com", "payowid", "예스주인");
        publicBook(owner, "예스책");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "payowid")
                        .with(user("pa-yvw@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yes24Enabled").value(true));
    }

    @Test
    @DisplayName("GET /api/profile yes24Enabled=false 시 응답에 false 반영")
    void profile_yes24Enabled_false_reflectedInResponse() throws Exception {
        when(yes24LinkBuilder.isEnabled()).thenReturn(false);
        register("pa-yvw2@booktimer.com", "payvw2id", "예스뷰어2");
        User owner = register("pa-yow2@booktimer.com", "payow2id", "예스주인2");
        publicBook(owner, "예스책2");

        mockMvc.perform(get("/api/profile")
                        .param("loginId", "payow2id")
                        .with(user("pa-yvw2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yes24Enabled").value(false));
    }

    @Test
    @DisplayName("GET /api/profile 응답에 isbn13·visibility·user 미노출 (Book DTO 화이트리스트)")
    void profile_bookDtoWhitelist_sensitiveFieldsAbsent() throws Exception {
        register("pa-wvw@booktimer.com", "pawvwid", "화이트뷰어");
        User owner = register("pa-wow@booktimer.com", "pawowid", "화이트주인");
        publicBook(owner, "화이트책");

        String body = mockMvc.perform(get("/api/profile")
                        .param("loginId", "pawowid")
                        .with(user("pa-wvw@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("\"isbn13\"")
                .doesNotContain("\"visibility\"")
                .doesNotContain("\"user\":");
    }

    // ── 12. null-state 조회 차단 (N-055 실표면) ──────────────────────────

    @Test
    @DisplayName("GET /api/profile loginId=null 사용자 픽스처 있어도 불가능한 핸들로는 404")
    void profile_nullLoginIdUser_queryByNullHandle_returns404() throws Exception {
        // null loginId 사용자 생성(온보딩 미완성 형태)
        registrationService.register("pa-null@booktimer.com", "pw1234qwer!!", "null상태유저", SEOUL, Role.USER, today());
        // viewer 생성
        register("pa-qvw@booktimer.com", "paqvwid", "조회뷰어");

        // null loginId 사용자의 (없는) 핸들로 조회 → findByLoginId("zz_no_login_id")가 empty → 404
        mockMvc.perform(get("/api/profile")
                        .param("loginId", "zz_no_login_id_xyz")
                        .with(user("pa-qvw@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    // ── 13. 격자 발광용 recency (lastStoryAt) ──────────────────
    // 서버는 「그 책 여백의 최근 글 시각」이라는 원시 사실만 준다 — 24시간 판정은 클라 순수 함수의 몴.
    // 프라이버시: 여백은 팔로워 전용 콘텐츠라, 비팔로워엔 전부 null이어야 한다(격자가 오늘과 동일).

    @Test
    @DisplayName("GET /api/profile 팔로워 → 글 있는 책만 lastStoryAt(최신 글 시각), 글 없는 책은 null")
    void profile_follower_getsLastStoryAtOnlyForBooksWithEntries() throws Exception {
        User viewer = register("rc-viewer@booktimer.com", "rcviewer", "열람자");
        User owner = register("rc-owner@booktimer.com", "rcowner", "주인");
        followRepository.save(Follow.of(viewer, owner));
        Book withEntries = publicBookOf(owner, "가 글 있는 책");
        publicBookOf(owner, "나 핑 빈 책");
        storyAt(owner, withEntries, "예전 글", Instant.parse("2026-08-01T00:00:00Z"));
        storyAt(owner, withEntries, "최신 글", Instant.parse("2026-08-10T09:00:00Z"));

        mockMvc.perform(get("/api/profile").param("loginId", "rcowner")
                        .with(user("rc-viewer@booktimer.com")))
                .andExpect(status().isOk())
                // 이름순 공급 — 「가 …」이 먼저
                .andExpect(jsonPath("$.books[0].title").value("가 글 있는 책"))
                .andExpect(jsonPath("$.books[0].lastStoryAt").value("2026-08-10T09:00:00Z"))
                .andExpect(jsonPath("$.books[1].title").value("나 핑 빈 책"))
                .andExpect(jsonPath("$.books[1].lastStoryAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/profile 비팔로워 → 글이 있어도 lastStoryAt 전부 null (여백은 팔로워 전용)")
    void profile_nonFollower_getsNoRecency() throws Exception {
        register("rc-nfviewer@booktimer.com", "rcnfviewer", "비팔로워");
        User owner = register("rc-nfowner@booktimer.com", "rcnfowner", "주인");
        Book book = publicBookOf(owner, "글 있는 책");
        storyAt(owner, book, "비팔로워에겐 안 보일 시각", Instant.parse("2026-08-10T09:00:00Z"));

        mockMvc.perform(get("/api/profile").param("loginId", "rcnfowner")
                        .with(user("rc-nfviewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].title").value("글 있는 책"))
                .andExpect(jsonPath("$.books[0].lastStoryAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/profile 본인 → 팔로우 없이도 lastStoryAt이 실린다")
    void profile_self_getsRecency() throws Exception {
        User me = register("rc-self@booktimer.com", "rcself", "나");
        Book book = publicBookOf(me, "내 책");
        storyAt(me, book, "내 글", Instant.parse("2026-08-10T09:00:00Z"));

        mockMvc.perform(get("/api/profile").param("loginId", "rcself")
                        .with(user("rc-self@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].lastStoryAt").value("2026-08-10T09:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/profile/books 팔로워 → 목록 필터 경로에도 lastStoryAt이 실린다")
    void books_follower_getsRecency() throws Exception {
        User viewer = register("rc-bviewer@booktimer.com", "rcbviewer", "열람자");
        User owner = register("rc-bowner@booktimer.com", "rcbowner", "주인");
        followRepository.save(Follow.of(viewer, owner));
        Book book = publicBookOf(owner, "글 있는 책");
        storyAt(owner, book, "글", Instant.parse("2026-08-10T09:00:00Z"));

        mockMvc.perform(get("/api/profile/books").param("loginId", "rcbowner")
                        .with(user("rc-bviewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].lastStoryAt").value("2026-08-10T09:00:00Z"));
    }

    /**
     * §5-1 ⓕ — 앵커(반전 아님). 비공개 책에도 글을 쓸 수 있게 된 뒤(2026-08-16 결정 2), 격자 발광 경로가
     * 「비공개 책이 있다」는 사실이 새는 새 통로가 되지 않는지 못 박는다. 방어는 이중이다:
     * 프로필 응답의 책 목록 자체가 PUBLIC-only라 그 책은 애초에 목록에 없고, recency도 따라서 붙지 않는다.
     */
    @Test
    @DisplayName("GET /api/profile/books 비공개 책에 글이 있어도 그 책·lastStoryAt은 목록에 없다 (발광 경로 재단언)")
    void books_privateBookWithStories_isAbsentEntirely() throws Exception {
        User viewer = register("rc-pvviewer@booktimer.com", "rcpvviewer", "열람자");
        User owner = register("rc-pvowner@booktimer.com", "rcpvowner", "주인");
        followRepository.save(Follow.of(viewer, owner));
        Book open = publicBookOf(owner, "가 공개 책");
        Book secret = bookRepository.save(
                Book.register(owner, "나 비공개 책", null, null, null, null, null, BookStatus.READING));
        storyAt(owner, open, "공개 책 글", Instant.parse("2026-08-10T09:00:00Z"));
        storyAt(owner, secret, "비공개 책 메모", Instant.parse("2026-08-11T09:00:00Z"));

        mockMvc.perform(get("/api/profile/books").param("loginId", "rcpvowner")
                        .with(user("rc-pvviewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].title").value("가 공개 책"))
                .andExpect(jsonPath("$.books[0].lastStoryAt").value("2026-08-10T09:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/profile 글이 한 장도 없는 사용자 → 전 책 null, 쿼리도 안 터진다 (null-state 경계)")
    void profile_userWithoutAnyStory_getsAllNull() throws Exception {
        User viewer = register("rc-emptyv@booktimer.com", "rcemptyv", "열람자");
        User owner = register("rc-empty@booktimer.com", "rcempty", "글 없는 사람");
        followRepository.save(Follow.of(viewer, owner));
        publicBookOf(owner, "책 하나");

        mockMvc.perform(get("/api/profile").param("loginId", "rcempty")
                        .with(user("rc-emptyv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].lastStoryAt").doesNotExist());
    }
}
