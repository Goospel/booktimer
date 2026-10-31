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

    private static String save(String title, String body, int revision, Boolean exported) {
        return "{\"title\":" + (title == null ? "null" : "\"" + title + "\"") + ",\"body\":\"" + body
                + "\",\"revision\":" + revision + ",\"exported\":" + exported + "}";
    }

    // ── REQ-216 옛 JS 호환(책생글 M1 — 설계 §5.1 표) ─────────────────────────────

    /** v1이 만든 글 한 편 — M1 뒤엔 앱이 drafts를 만들 수 없어 저장소로 직접 둔다. */
    private WritingDraft draft(User user, Book book, String title, String body) {
        WritingDraft d = WritingDraft.start(user, book);
        d.edit(title, body, 0);
        return draftRepository.saveAndFlush(d);
    }

    /** V105 복사가 남기는 짝 — writing_draft.story_id. */
    private void movedTo(WritingDraft d, Story s) {
        em.createNativeQuery("update writing_draft set story_id = ?1 where id = ?2")
                .setParameter(1, s.getId()).setParameter(2, d.getId()).executeUpdate();
        em.clear();
    }

    private static final String MOVED = "이 글은 여백으로 옮겨졌어요 — 앱을 닫았다가 다시 열어 주세요";

    @Test
    @DisplayName("REQ-216 · 옮긴 글 · 안 옮긴 글 PUT 둘 다 410 · 본문 평문(JSON 아님) · 글은 그대로")
    void putIs410ForAnyRow() throws Exception {
        User me = register("wput410", Role.USER);
        String token = apiTokenService.issue(me);
        Book book = book(me, "책");
        WritingDraft moved = draft(me, book, "제목", "옮긴 글");
        movedTo(moved, margin(me, book, null, "제목\n\n옮긴 글"));
        WritingDraft unmoved = draft(me, book, null, "안 옮긴 글");

        for (WritingDraft d : java.util.List.of(moved, unmoved)) {
            json(put("/api/app/drafts/" + d.getId()), token, save(null, "옛 JS가 보낸 글", 1, null))
                    .andExpect(status().isGone())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string(MOVED));
        }
        assertThat(draftRepository.findById(unmoved.getId()).orElseThrow().getBody()).isEqualTo("안 옮긴 글");
    }

    @Test
    @DisplayName("REQ-216 · GET movedTo = story id · 안 옮긴 행 null · 목록 GET은 200(읽기 전용)")
    void getCarriesMovedTo() throws Exception {
        User me = register("wmoved", Role.USER);
        String token = apiTokenService.issue(me);
        Book book = book(me, "책");
        WritingDraft moved = draft(me, book, null, "옮긴 글");
        Story note = margin(me, book, null, "옮긴 글");
        movedTo(moved, note);
        WritingDraft unmoved = draft(me, book, null, "안 옮긴 글");

        call(get("/api/app/drafts/" + moved.getId()), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.movedTo").value(note.getId()));
        call(get("/api/app/drafts/" + unmoved.getId()), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.movedTo").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.body").value("안 옮긴 글"));
        call(get("/api/app/drafts"), token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // 남의 글 id는 여전히 404(존재 비노출)
        call(get("/api/app/drafts/" + moved.getId()), apiTokenService.issue(register("wmovedthief", Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("REQ-216 · POST · ai · DELETE 410 — 표를 얼린다(글 수 그대로)")
    void writesAre410() throws Exception {
        User me = register("wfrozen", Role.USER);
        String token = apiTokenService.issue(me);
        Book book = book(me, "책");
        WritingDraft d = draft(me, book, null, "얼린 글");

        json(post("/api/app/drafts"), token, "{\"bookId\":" + book.getId() + "}")
                .andExpect(status().isGone()).andExpect(content().string(MOVED));
        json(post("/api/app/drafts/" + d.getId() + "/ai"), token, "{\"step\":\"ASK\",\"revision\":1}")
                .andExpect(status().isGone()).andExpect(content().string(MOVED));
        call(delete("/api/app/drafts/" + d.getId()), token)
                .andExpect(status().isGone()).andExpect(content().string(MOVED));
        assertThat(draftRepository.findTop20ByUserOrderByUpdatedAtDesc(me)).hasSize(1);
    }

    @Test
    @DisplayName("U-154 ⑤ · 옮긴 쪽지를 지우면 writing_draft 원본도 0행 · GET drafts/{id} 404 · 목록에서 빠짐(처리방침 5절 파기)")
    void deletingMovedNoteDeletesSourceDraft() throws Exception {
        User me = register("wdelsrc", Role.USER);
        String token = apiTokenService.issue(me);
        Book book = book(me, "책");
        WritingDraft moved = draft(me, book, "제목", "옮긴 글");
        Story note = margin(me, book, null, "제목\n\n옮긴 글");
        movedTo(moved, note);
        WritingDraft other = draft(me, book, null, "남은 글");

        call(delete("/api/app/margins/" + note.getId()), token).andExpect(status().isNoContent());

        assertThat(draftRepository.findById(moved.getId())).isEmpty();
        call(get("/api/app/drafts/" + moved.getId()), token).andExpect(status().isNotFound());
        call(get("/api/app/drafts"), token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertThat(draftRepository.findById(other.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-216 · materials drafts = [] — 그 책에 옛 글이 있어도(옛 책 화면이 drafts.length를 읽는다)")
    void materialsDraftsEmpty() throws Exception {
        User me = register("wmatdrafts", Role.USER);
        Book book = book(me, "책");
        draft(me, book, null, "옛 글");

        call(get("/api/app/books/" + book.getId() + "/materials"), apiTokenService.issue(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drafts").isArray())
                .andExpect(jsonPath("$.drafts.length()").value(0));
    }
    // ── REQ-11 재료는 소셜 경로를 타지 않는다 ─────────────────────────────────────

    @Test
    @DisplayName("REQ-12 · materials는 그 책의 여백만 최근순으로 싣는다(글은 M1부터 drafts = [] — REQ-216)")
    void materialsAreThisBookRecentFirst() throws Exception {
        User me = register("wmatorder", Role.USER);
        String token = apiTokenService.issue(me);
        Book a = book(me, "책 A");
        Book b = book(me, "책 B");
        margin(me, a, null, "먼저 쓴 여백");
        margin(me, a, null, "나중 쓴 여백"); // createdAt이 같아도 id 내림차순으로 순서가 정해진다
        margin(me, b, null, "다른 책 여백");

        call(get("/api/app/books/" + a.getId() + "/materials"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margins.length()").value(2))
                .andExpect(jsonPath("$.margins[0].text").value("나중 쓴 여백"))
                .andExpect(jsonPath("$.margins[1].text").value("먼저 쓴 여백"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("다른 책 여백"))))
                .andExpect(jsonPath("$.aiIncluded").value(2))
                .andExpect(jsonPath("$.drafts.length()").value(0));
    }

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
    @DisplayName("REQ-13 · 책을 지우면 그 책의 글·여백은 남고 book이 null이 된다(실 H2 — 여백까지 넓힘, C9)")
    void deletingBookKeepsDraft() throws Exception {
        User me = register("wdelbook", Role.USER);
        Book book = book(me, "지울 책");
        Long marginId = margin(me, book, null, "책이 사라져도 남을 여백").getId();
        Long id = draft(me, book, null, "남아야 할 글").getId();

        bookService.delete(me, book.getId());
        em.flush();
        em.clear();

        WritingDraft draft = draftRepository.findById(id).orElseThrow();
        assertThat(draft.getBook()).isNull();
        assertThat(draft.getBody()).isEqualTo("남아야 할 글");
        assertThat(bookRepository.findById(book.getId())).isEmpty();
        assertThat(storyRepository.findById(marginId).orElseThrow().getBook()).as("여백도 남고 연결만 풀린다(C9)").isNull();
    }

    @Test
    @DisplayName("REQ-14 · 글을 가진 계정의 탈퇴가 FK 위반 없이 글까지 지운다(실 H2)")
    void purgeDeletesDrafts() throws Exception {
        String sub = "000777.writing.0014";
        User me = appleUsers.register(sub);
        Long id = draft(me, book(me, "앱 책"), null, "").getId();
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
        Long id = draft(me, book(me, "책"), null, "").getId();

        me.consentToAi(EFFECTIVE.minusSeconds(1));
        userRepository.saveAndFlush(me);
        call(get("/api/app/drafts/" + id), token).andExpect(jsonPath("$.aiConsent").value(false));

        // 양성 대조 — 시행일 정각 이후의 동의는 유효하다.
        me.consentToAi(EFFECTIVE);
        userRepository.saveAndFlush(me);
        call(get("/api/app/drafts/" + id), token).andExpect(jsonPath("$.aiConsent").value(true));
    }

    @Test
    @DisplayName("REQ-154 · GET /api/app/ai-consent는 지금 동의 상태와 시각을 준다(시행일보다 이른 시각은 동의 없음)")
    void consentViewReadsState() throws Exception {
        clock.set(EFFECTIVE.plusSeconds(86_400 * 3));
        User me = register("wconsview", Role.USER);
        String token = apiTokenService.issue(me);

        call(get("/api/app/ai-consent"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiConsent").value(false))
                .andExpect(jsonPath("$.aiConsentAt").doesNotExist());

        me.consentToAi(EFFECTIVE);
        userRepository.saveAndFlush(me);
        call(get("/api/app/ai-consent"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiConsent").value(true))
                .andExpect(jsonPath("$.aiConsentAt").value(EFFECTIVE.toString()));

        // 같은 계정 · 입력만 바꾼 대조 — 시행일 1초 전 시각은 동의 없음
        me.consentToAi(EFFECTIVE.minusSeconds(1));
        userRepository.saveAndFlush(me);
        call(get("/api/app/ai-consent"), token).andExpect(jsonPath("$.aiConsent").value(false));

        mvc.perform(get("/api/app/ai-consent")).andExpect(status().isUnauthorized());
    }

    // ── REQ-16 내보내기 계측 → 쪽지로 옮겼다(REQ-227 · AppMarginApiControllerTest) ─────────

    @Test
    @DisplayName("REQ-16 · 쪽지 exported는 처음 한 번만 시각을 박는다(REQ-227 — v1 지표는 쪽지 기준으로 산다)")
    void exportedStampsOnce() throws Exception {
        Instant first = Instant.parse("2026-11-24T01:00:00Z");
        clock.set(first.minusSeconds(60));
        User me = register("wexport", Role.USER);
        String token = apiTokenService.issue(me);
        Long id = margin(me, book(me, "책"), null, "글").getId();

        clock.set(first);
        json(put("/api/app/margins/" + id), token, "{\"text\":\"글\",\"revision\":0,\"exported\":true}").andExpect(status().isOk());
        clock.set(first.plusSeconds(3_600));
        json(put("/api/app/margins/" + id), token, "{\"text\":\"글 고침\",\"revision\":1,\"exported\":true}").andExpect(status().isOk());

        assertThat(storyRepository.findById(id).orElseThrow().getExportedAt()).isEqualTo(first);
    }

    // ── REQ-48 독서 서재에만 ──────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-48 · 공부 서재 책 id로 쪽지를 만들면 404이고 같은 사용자의 독서 서재 책 id는 201")
    void studyBookIdIs404() throws Exception {
        User me = register("wstudy", Role.USER);
        String token = apiTokenService.issue(me);
        StudyBook study = studyBookRepository.saveAndFlush(StudyBook.register(me, "정보처리기사", "저자", null, null, null, null));
        Book reading = book(me, "데미안");
        if (reading.getId().equals(study.getId())) { // 두 테이블의 id가 우연히 같으면 404 단언이 무의미해진다
            bookRepository.delete(reading);
            reading = book(me, "데미안");
        }

        long before = storyRepository.count();
        json(post("/api/app/margins"), token, "{\"bookId\":" + study.getId() + ",\"text\":\"생각\"}").andExpect(status().isNotFound());
        assertThat(storyRepository.count()).isEqualTo(before);
        json(post("/api/app/margins"), token, "{\"bookId\":" + reading.getId() + ",\"text\":\"생각\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookTitle").value("데미안"))
                .andExpect(jsonPath("$.text").value("생각"))
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
