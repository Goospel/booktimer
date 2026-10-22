package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.testsupport.MessengerTest;
import com.booktimer.testsupport.MutableClock;
import com.booktimer.user.AccountService;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import com.booktimer.writing.WritingDraft;
import com.booktimer.writing.WritingDraftRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱 글 원장 · 책 재료 · AI 동의 API(v1 REQ-10·11·13·14·15·16·48) — 실 H2 + Bearer 토큰.
 *
 * <p>가변 시계가 필요해(REQ-15 시행일 경계) 메신저 세계({@link MessengerTest})의 컨텍스트를 빌려 쓴다 — 새 세계를 띄우지
 * 않으려고(T-235 예산). 그 세계엔 {@code @AutoConfigureMockMvc}가 없어 MockMvc를 보안 필터째 직접 만든다.
 */
@MessengerTest
@Transactional
class WritingApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    /** 처리방침 6-3절 시행일 2026-11-23 00:00 KST. */
    private static final Instant EFFECTIVE = Instant.parse("2026-11-22T15:00:00Z");

    @Autowired WebApplicationContext wac;
    @Autowired MutableClock clock;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired AppleUserProvisioningService appleUsers;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BookRepository bookRepository;
    @Autowired BookService bookService;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired WritingDraftRepository draftRepository;
    @Autowired AccountService accountService;
    @Autowired EntityManager em;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    // ── 준비 ─────────────────────────────────────────────────────────────────

    private User register(String loginId, Role role) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId, "닉네임_" + loginId,
                SEOUL, role, LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private Book book(User user, String title) {
        return bookRepository.saveAndFlush(Book.register(user, title, "저자", null, null, null, null, BookStatus.READING));
    }

    private Story margin(User user, Book book, String quote, String text) {
        return storyRepository.saveAndFlush(Story.of(user, text, book, "paper", quote));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private Long createDraft(String token, Long bookId) throws Exception {
        String res = json(post("/api/app/drafts"), token, "{\"bookId\":" + bookId + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(res.replaceAll("(?s)^\\{\"id\":(\\d+),.*", "$1"));
    }

    private static String save(String title, String body, int revision, Boolean exported) {
        return "{\"title\":" + (title == null ? "null" : "\"" + title + "\"") + ",\"body\":\"" + body
                + "\",\"revision\":" + revision + ",\"exported\":" + exported + "}";
    }

    // ── REQ-10 글 원장 CRUD ────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-10 · 남의 글 id는 조회·저장·삭제 모두 404")
    void othersDraftIs404() throws Exception {
        User owner = register("wowner", Role.USER);
        String ownerToken = apiTokenService.issue(owner);
        String thiefToken = apiTokenService.issue(register("wthief", Role.USER));
        Long id = createDraft(ownerToken, book(owner, "주인의 책").getId());
        json(put("/api/app/drafts/" + id), ownerToken, save(null, "주인의 글", 0, null)).andExpect(status().isOk());

        call(get("/api/app/drafts/" + id), thiefToken).andExpect(status().isNotFound());
        json(put("/api/app/drafts/" + id), thiefToken, save(null, "덮어쓰기", 1, null)).andExpect(status().isNotFound());
        call(delete("/api/app/drafts/" + id), thiefToken).andExpect(status().isNotFound());

        WritingDraft after = draftRepository.findById(id).orElseThrow();
        assertThat(after.getBody()).isEqualTo("주인의 글");
        assertThat(after.getRevision()).isEqualTo(1);
        // 양성 대조 — 주인은 지울 수 있다(위 404가 「삭제 문이 아예 없는」 구현으로 통과하지 않게).
        call(delete("/api/app/drafts/" + id), ownerToken).andExpect(status().isNoContent());
        assertThat(draftRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("REQ-10 · 낡은 revision 저장은 409이고 본문이 그대로다")
    void staleRevisionIs409() throws Exception {
        User me = register("wstale", Role.USER);
        String token = apiTokenService.issue(me);
        Long id = createDraft(token, book(me, "책").getId());

        json(put("/api/app/drafts/" + id), token, save("제목", "긴 글", 0, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        json(put("/api/app/drafts/" + id), token, save("제목", "짧은 글", 0, null))
                .andExpect(status().isConflict());

        call(get("/api/app/drafts/" + id), token)
                .andExpect(jsonPath("$.body").value("긴 글"))
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    @DisplayName("REQ-10 · 10,001자 본문은 400")
    void tooLongBodyIs400() throws Exception {
        User me = register("wlong", Role.USER);
        String token = apiTokenService.issue(me);
        Long id = createDraft(token, book(me, "책").getId());

        json(put("/api/app/drafts/" + id), token, save(null, "가".repeat(10_001), 0, null))
                .andExpect(status().isBadRequest());
        assertThat(draftRepository.findById(id).orElseThrow().getBody()).isEmpty();
        // 양성 대조 — 상한 그대로는 저장된다.
        json(put("/api/app/drafts/" + id), token, save(null, "가".repeat(10_000), 0, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    @DisplayName("REQ-10 · 목록에 책 연결이 풀린 글도 나온다")
    void listIncludesUnlinkedDraft() throws Exception {
        User me = register("wlist", Role.USER);
        String token = apiTokenService.issue(me);
        Book doomed = book(me, "지울 책");
        Long unlinked = createDraft(token, doomed.getId());
        Long linked = createDraft(token, book(me, "남길 책").getId());
        json(put("/api/app/drafts/" + unlinked), token, save(null, "\\n  책 없는 글\\n둘째 줄", 0, null)).andExpect(status().isOk());
        bookService.delete(me, doomed.getId());

        call(get("/api/app/drafts"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(unlinked))
                .andExpect(jsonPath("$[0].bookId").doesNotExist())
                .andExpect(jsonPath("$[0].preview").value("책 없는 글"))
                .andExpect(jsonPath("$[1].id").value(linked))
                .andExpect(jsonPath("$[1].bookTitle").value("남길 책"));
    }

    // ── REQ-11 재료는 소셜 경로를 타지 않는다 ─────────────────────────────────────

    @Test
    @DisplayName("REQ-11 · ADMIN 본인도 자기 책 재료를 받는다")
    void adminGetsOwnMaterials() throws Exception {
        User admin = register("wadmin", Role.ADMIN);
        Book book = book(admin, "관리자의 책");
        margin(admin, book, "옮긴 문장", "관리자의 여백");

        call(get("/api/app/books/" + book.getId() + "/materials"), apiTokenService.issue(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.book.title").value("관리자의 책"))
                .andExpect(jsonPath("$.margins[0].text").value("관리자의 여백"))
                .andExpect(jsonPath("$.margins[0].quote").value("옮긴 문장"))
                .andExpect(jsonPath("$.aiIncluded").value(1))
                .andExpect(jsonPath("$.aiExcluded").value(0));
    }

    @Test
    @DisplayName("REQ-11 · 핸들 없는 Apple 계정도 자기 책 재료를 받는다")
    void handlelessAppleAccountGetsMaterials() throws Exception {
        User apple = appleUsers.register("000777.writing.0011");
        assertThat(apple.getLoginId()).as("전제 — 핸들 없는 계정").isNull();
        Book book = book(apple, "앱에서 담은 책");
        margin(apple, book, null, "앱에서 쓴 여백");

        call(get("/api/app/books/" + book.getId() + "/materials"), apiTokenService.issue(apple))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margins[0].text").value("앱에서 쓴 여백"))
                .andExpect(jsonPath("$.book.readSeconds").value(0))
                .andExpect(jsonPath("$.book.sessions").value(0));
    }

    @Test
    @DisplayName("REQ-11 · 남의 책 id는 404")
    void othersBookIs404() throws Exception {
        User owner = register("wbookowner", Role.USER);
        Book book = book(owner, "주인의 책");
        margin(owner, book, null, "남이 보면 안 되는 여백");

        call(get("/api/app/books/" + book.getId() + "/materials"), apiTokenService.issue(register("wpeek", Role.USER)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("여백"))));
    }

    // ── REQ-13 · 14 책 삭제 · 탈퇴 ───────────────────────────────────────────────

    @Test
    @DisplayName("REQ-13 · 책을 지우면 그 책의 글은 남고 book이 null이 된다(실 H2)")
    void deletingBookKeepsDraft() throws Exception {
        User me = register("wdelbook", Role.USER);
        String token = apiTokenService.issue(me);
        Book book = book(me, "지울 책");
        Long marginId = margin(me, book, null, "책과 함께 사라질 여백").getId();
        Long id = createDraft(token, book.getId());
        json(put("/api/app/drafts/" + id), token, save(null, "남아야 할 글", 0, null)).andExpect(status().isOk());

        bookService.delete(me, book.getId());
        em.flush();
        em.clear();

        WritingDraft draft = draftRepository.findById(id).orElseThrow();
        assertThat(draft.getBook()).isNull();
        assertThat(draft.getBody()).isEqualTo("남아야 할 글");
        assertThat(bookRepository.findById(book.getId())).isEmpty();
        assertThat(storyRepository.findById(marginId)).as("여백은 기존대로 책과 함께 지워진다").isEmpty();
    }

    @Test
    @DisplayName("REQ-14 · 글을 가진 계정의 탈퇴가 FK 위반 없이 글까지 지운다(실 H2)")
    void purgeDeletesDrafts() throws Exception {
        String sub = "000777.writing.0014";
        User me = appleUsers.register(sub);
        String token = apiTokenService.issue(me);
        Long id = createDraft(token, book(me, "앱 책").getId());
        em.flush();

        accountService.deleteAppleVerifiedAccount(me, sub);
        em.flush();

        assertThat(draftRepository.findById(id)).isEmpty();
        assertThat(userRepository.findByAppleSub(sub)).isEmpty();
    }

    // ── REQ-15 AI 동의 · 시행일 ─────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-15 · 시행일 전 동의는 503이고 시각이 박히지 않는다")
    void consentBeforeEffectiveIs503() throws Exception {
        clock.set(EFFECTIVE.minusSeconds(1)); // 2026-11-23 00:00 KST 1초 전
        User me = register("wearly", Role.USER);

        json(put("/api/app/ai-consent"), apiTokenService.issue(me), "{\"agree\":true}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("AI 질문은 11월 23일부터 열려요"));

        assertThat(userRepository.findById(me.getId()).orElseThrow().getAiConsentAt()).isNull();
    }

    @Test
    @DisplayName("REQ-15 · 시행일 당일 동의는 시각이 박히고 agree=false는 지운다")
    void consentOnEffectiveDay() throws Exception {
        clock.set(EFFECTIVE); // 2026-11-23 00:00 KST 정각
        User me = register("wontime", Role.USER);
        String token = apiTokenService.issue(me);

        json(put("/api/app/ai-consent"), token, "{\"agree\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiConsent").value(true));
        assertThat(userRepository.findById(me.getId()).orElseThrow().getAiConsentAt()).isEqualTo(EFFECTIVE);

        json(put("/api/app/ai-consent"), token, "{\"agree\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiConsent").value(false));
        assertThat(userRepository.findById(me.getId()).orElseThrow().getAiConsentAt()).isNull();
    }

    @Test
    @DisplayName("REQ-15 · 시행일보다 이른 동의 시각은 동의 없음으로 본다")
    void consentBeforeEffectiveIsInvalid() throws Exception {
        clock.set(EFFECTIVE.plusSeconds(86_400 * 3));
        User me = register("wstalecons", Role.USER);
        String token = apiTokenService.issue(me);
        Long id = createDraft(token, book(me, "책").getId());

        me.consentToAi(EFFECTIVE.minusSeconds(1));
        userRepository.saveAndFlush(me);
        call(get("/api/app/drafts/" + id), token).andExpect(jsonPath("$.aiConsent").value(false));

        // 양성 대조 — 시행일 정각 이후의 동의는 유효하다.
        me.consentToAi(EFFECTIVE);
        userRepository.saveAndFlush(me);
        call(get("/api/app/drafts/" + id), token).andExpect(jsonPath("$.aiConsent").value(true));
    }

    // ── REQ-16 내보내기 계측 ─────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-16 · exported는 처음 한 번만 시각을 박는다")
    void exportedStampsOnce() throws Exception {
        Instant first = Instant.parse("2026-11-24T01:00:00Z");
        clock.set(first.minusSeconds(60));
        User me = register("wexport", Role.USER);
        String token = apiTokenService.issue(me);
        Long id = createDraft(token, book(me, "책").getId());

        json(put("/api/app/drafts/" + id), token, save(null, "글", 0, null)).andExpect(status().isOk());
        assertThat(draftRepository.findById(id).orElseThrow().getExportedAt()).as("exported 없이는 안 박힌다").isNull();
        clock.set(first);
        json(put("/api/app/drafts/" + id), token, save(null, "글", 1, true)).andExpect(status().isOk());
        clock.set(first.plusSeconds(3_600));
        json(put("/api/app/drafts/" + id), token, save(null, "글 고침", 2, true)).andExpect(status().isOk());

        assertThat(draftRepository.findById(id).orElseThrow().getExportedAt()).isEqualTo(first);
        call(get("/api/app/drafts"), token).andExpect(jsonPath("$[0].exported").value(true));
    }

    // ── REQ-48 독서 서재에만 ──────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-48 · 공부 서재 책 id로 글을 만들면 404이고 같은 사용자의 독서 서재 책 id는 201")
    void studyBookIdIs404() throws Exception {
        User me = register("wstudy", Role.USER);
        String token = apiTokenService.issue(me);
        StudyBook study = studyBookRepository.saveAndFlush(StudyBook.register(me, "정보처리기사", "저자", null, null, null, null));
        Book reading = book(me, "데미안");
        if (reading.getId().equals(study.getId())) { // 두 테이블의 id가 우연히 같으면 404 단언이 무의미해진다
            bookRepository.delete(reading);
            reading = book(me, "데미안");
        }

        json(post("/api/app/drafts"), token, "{\"bookId\":" + study.getId() + "}").andExpect(status().isNotFound());
        assertThat(draftRepository.findTop20ByUserOrderByUpdatedAtDesc(me)).isEmpty();
        json(post("/api/app/drafts"), token, "{\"bookId\":" + reading.getId() + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookTitle").value("데미안"))
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.questions.rounds.length()").value(0));
    }

    @Test
    @DisplayName("REQ-48 · materials 응답에 공부 재료 개수 필드가 없다")
    void materialsHasNoStudyFields() throws Exception {
        User me = register("wnostudy", Role.USER);
        Book book = book(me, "책");

        call(get("/api/app/books/" + book.getId() + "/materials"), apiTokenService.issue(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiConsent").value(false))
                .andExpect(jsonPath("$.drafts").isArray())
                .andExpect(jsonPath("$.studyNotes").doesNotExist())
                .andExpect(jsonPath("$.recalls").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist());
    }
}
