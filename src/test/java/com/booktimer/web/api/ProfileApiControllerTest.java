package com.booktimer.web.api;

import com.booktimer.block.BlockService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.personality.ReadingPersonalityCache;
import com.booktimer.personality.ReadingPersonalityCacheRepository;
import com.booktimer.report.ReportReason;
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
    private ReadingPersonalityCacheRepository personalityCacheRepository;

    @Autowired
    private Clock clock;

    @MockitoBean
    private CoupangLinkBuilder coupangLinkBuilder;

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
                .andExpect(jsonPath("$.coupangEnabled").isBoolean());
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
}
