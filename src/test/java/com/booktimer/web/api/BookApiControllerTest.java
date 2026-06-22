package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * /api/books/* JSON API 통합 테스트 (선별 SPA 단계 3).
 * IDOR·DTO 화이트리스트·멱등·FK 정리·CSRF가 핵심 경계.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired BookService bookService;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired Clock clock;
    @MockitoBean CoupangLinkBuilder coupangLinkBuilder;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private Book addBook(User u, String title, String isbn13, BookStatus status) {
        return bookService.addFromSearch(u,
                new BookSearchResult(title, null, isbn13, null, null, null, null, null), status);
    }

    // ── 1. 미인증 → 302 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books: 미인증이면 /login으로 302")
    void shelf_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/books/search: 미인증이면 /login으로 302")
    void search_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/books/search").param("q", "클린코드"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── 2. GET /api/books 내 책만·필드 (status/visibility name+label 공존) ──

    @Test
    @DisplayName("GET /api/books: 내 책만 반환, status name·label·visibility name·label 공존, myLoginId·searchEnabled")
    void shelf_returnsMyBooksWithNameAndLabel() throws Exception {
        User me = register("me@a.com", "mybooks", "북리더");
        addBook(me, "클린코드", "9788966260959", BookStatus.READING);

        mockMvc.perform(get("/api/books").with(user("me@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].status", is("READING")))
                .andExpect(jsonPath("$.books[0].statusLabel", is("읽는 중")))
                .andExpect(jsonPath("$.books[0].visibility", is("PRIVATE")))
                .andExpect(jsonPath("$.books[0].visibilityLabel", is("비공개")))
                .andExpect(jsonPath("$.myLoginId", is("mybooks")))
                .andExpect(jsonPath("$.searchEnabled", is(false)));
    }

    // ── 3. DTO 화이트리스트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/books: Book 엔티티 직렬화 금지 — user·clickCount·coupangClickCount 없음")
    void shelf_dtoWhitelist() throws Exception {
        User me = register("dto@a.com", "dtouser", "화이트");
        addBook(me, "테스트책", null, BookStatus.WANT_TO_READ);

        mockMvc.perform(get("/api/books").with(user("dto@a.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"user\""))))
                .andExpect(content().string(not(containsString("clickCount"))))
                .andExpect(content().string(not(containsString("coupangClickCount"))));
    }

    // ── 4. N-055: isbn null 책은 popularity 맵에 키 없음 ────────────────────

    @Test
    @DisplayName("GET /api/books: isbn null 책은 popularity 맵에 키 없음 (N-055)")
    void shelf_nullIsbnNotInPopularity() throws Exception {
        User me = register("null@a.com", "nullisbn", "널리더");
        addBook(me, "수동추가", null, BookStatus.READING);

        mockMvc.perform(get("/api/books").with(user("null@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.popularity").isEmpty());
    }

    // ── 5b. SearchRow.from 단위 테스트 (검색 stub 부재 보강 가드) ────────────

    @Test
    @DisplayName("SearchRow.from: isbn null → owned=false (N-055)")
    void searchRowFrom_nullIsbn_ownedFalse() {
        var r = new BookSearchResult("책", null, null, null, null, null, null, null);
        var row = BookApiController.SearchRow.from(r, Set.of("9780000000000"));
        assertThat(row.owned()).isFalse();
    }

    @Test
    @DisplayName("SearchRow.from: isbn이 myIsbns에 있으면 owned=true")
    void searchRowFrom_isbnInMyIsbns_ownedTrue() {
        var r = new BookSearchResult("책", null, "9788900000001", null, null, null, null, null);
        var row = BookApiController.SearchRow.from(r, Set.of("9788900000001"));
        assertThat(row.owned()).isTrue();
    }

    @Test
    @DisplayName("SearchRow.from: isbn이 myIsbns에 없으면 owned=false")
    void searchRowFrom_isbnNotInMyIsbns_ownedFalse() {
        var r = new BookSearchResult("책", null, "9788900000002", null, null, null, null, null);
        var row = BookApiController.SearchRow.from(r, Set.of("9788900000001"));
        assertThat(row.owned()).isFalse();
    }

    // ── 6. CSRF 없음 → 403 ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/books: CSRF 누락 → 403")
    void add_withoutCsrf_returns403() throws Exception {
        register("csrf1@a.com", "csrftest1", "유저1");
        mockMvc.perform(post("/api/books")
                        .with(user("csrf1@a.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"책\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/books/{id}/status: CSRF 누락 → 403")
    void changeStatus_withoutCsrf_returns403() throws Exception {
        User me = register("csrf2@a.com", "csrftest2", "유저2");
        Book book = addBook(me, "책", null, BookStatus.READING);
        mockMvc.perform(post("/api/books/{id}/status", book.getId())
                        .with(user("csrf2@a.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/books/{id}/visibility: CSRF 누락 → 403")
    void setVisibility_withoutCsrf_returns403() throws Exception {
        User me = register("csrf3@a.com", "csrftest3", "유저3");
        Book book = addBook(me, "책", null, BookStatus.READING);
        mockMvc.perform(post("/api/books/{id}/visibility", book.getId())
                        .with(user("csrf3@a.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/books/{id}/delete: CSRF 누락 → 403")
    void delete_withoutCsrf_returns403() throws Exception {
        User me = register("csrf4@a.com", "csrftest4", "유저4");
        Book book = addBook(me, "책", null, BookStatus.READING);
        mockMvc.perform(post("/api/books/{id}/delete", book.getId())
                        .with(user("csrf4@a.com")))
                .andExpect(status().isForbidden());
    }

    // ── 7. add 정상 → 200 + DB 반영 ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/books: 책 추가 → 200 + title·status name·statusLabel + DB 반영")
    void add_savesBook() throws Exception {
        User me = register("add@a.com", "adder", "추가자");
        mockMvc.perform(post("/api/books")
                        .with(user("add@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"클린코드\",\"status\":\"READING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("클린코드")))
                .andExpect(jsonPath("$.status", is("READING")))
                .andExpect(jsonPath("$.statusLabel", is("읽는 중")));

        assertThat(bookRepository.findByUserOrderByCreatedAtDesc(me)).hasSize(1);
    }

    // ── 7b. add category·pubDate 적재 ───────────────────────────────────────

    @Test
    @DisplayName("POST /api/books: category·pubDate 포함 → DB에 적재됨")
    void add_persistsCatalogMetadata() throws Exception {
        User me = register("meta@a.com", "metauser", "메타자");
        mockMvc.perform(post("/api/books")
                        .with(user("meta@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"한국소설책\",\"isbn13\":\"9788900000001\"," +
                                "\"category\":\"국내도서>소설/시/희곡>한국소설\",\"pubDate\":\"2020-03-15\"," +
                                "\"status\":\"WANT_TO_READ\"}"))
                .andExpect(status().isOk());

        Book saved = bookRepository.findByUserOrderByCreatedAtDesc(me).get(0);
        assertThat(saved.getCategory()).isEqualTo("국내도서>소설/시/희곡>한국소설");
        assertThat(saved.getPubDate()).isEqualTo("2020-03-15");
    }

    // ── 8. add 멱등 (isbn 있으면 기존 반환) ─────────────────────────────────

    @Test
    @DisplayName("POST /api/books: 같은 isbn 재추가 → 200 + 기존 id + 새 행 없음")
    void add_idempotentByIsbn() throws Exception {
        User me = register("idem@a.com", "idemer", "멱등자");
        Book existing = addBook(me, "기존책", "9788900000099", BookStatus.WANT_TO_READ);

        mockMvc.perform(post("/api/books")
                        .with(user("idem@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"기존책재추가\",\"isbn13\":\"9788900000099\",\"status\":\"READING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(existing.getId().intValue())));

        assertThat(bookRepository.findByUserOrderByCreatedAtDesc(me)).hasSize(1);
    }

    // ── 9. add 수동 (isbn null) 중복 허용 ───────────────────────────────────

    @Test
    @DisplayName("POST /api/books: isbn null 수동 2회 추가 → 2행 생성")
    void add_manual_duplicateAllowed() throws Exception {
        User me = register("manual@a.com", "manualer", "수동자");
        String json = "{\"title\":\"수동책\",\"status\":\"WANT_TO_READ\"}";

        mockMvc.perform(post("/api/books").with(user("manual@a.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
        mockMvc.perform(post("/api/books").with(user("manual@a.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());

        assertThat(bookRepository.findByUserOrderByCreatedAtDesc(me)).hasSize(2);
    }

    // ── 9b. add status 반영 ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/books: status=READING 추가 → 응답·DB 모두 READING (UX 회귀 가드)")
    void add_statusReflected() throws Exception {
        User me = register("status@a.com", "statusadder", "상태자");
        mockMvc.perform(post("/api/books")
                        .with(user("status@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"상태책\",\"status\":\"READING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READING")));

        assertThat(bookRepository.findByUserOrderByCreatedAtDesc(me).get(0).getStatus())
                .isEqualTo(BookStatus.READING);
    }

    // ── 10. changeStatus 정상 ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/books/{id}/status: 상태 변경 → 200 + status name·statusLabel 변경 + DB 반영")
    void changeStatus_returnsUpdated() throws Exception {
        User me = register("cst@a.com", "cstusr", "상태변경");
        Book book = addBook(me, "책", null, BookStatus.READING);

        mockMvc.perform(post("/api/books/{id}/status", book.getId())
                        .with(user("cst@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FINISHED")))
                .andExpect(jsonPath("$.statusLabel", is("완독")));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStatus())
                .isEqualTo(BookStatus.FINISHED);
    }

    // ── 10b. changeStatus 응답 seconds = 그 책의 누적 시간(단건 조회 — 전체 재집계 아님) ──

    @Test
    @DisplayName("POST /api/books/{id}/status: 응답 seconds는 해당 책의 누적 시간과 일치한다 (단건 집계 경로)")
    void changeStatus_secondsMatchesBookTotal() throws Exception {
        User me = register("sec@a.com", "secusr", "초확인");
        Book book = addBook(me, "시간책", null, BookStatus.READING);
        // 책에 완료 세션 1시간 추가
        ReadingSession s = ReadingSession.start(me, java.time.Instant.now(), book);
        s.end(java.time.Instant.now().plusSeconds(3600));
        sessionRepository.save(s);

        mockMvc.perform(post("/api/books/{id}/status", book.getId())
                        .with(user("sec@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seconds", is(3600)));
    }

    // ── 11. changeStatus IDOR → 404 (csrf() 필수 — 없으면 403이 IDOR 가드를 가림) ──

    @Test
    @DisplayName("POST /api/books/{id}/status: 남의 책 → 404, 남의 책 status 불변 (IDOR)")
    void changeStatus_idor_returns404() throws Exception {
        User attacker = register("att@a.com", "attacker11", "공격자");
        User victim = register("vic@a.com", "victim11", "피해자");
        Book victimBook = addBook(victim, "피해자책", null, BookStatus.WANT_TO_READ);

        mockMvc.perform(post("/api/books/{id}/status", victimBook.getId())
                        .with(user("att@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READING\"}"))
                .andExpect(status().isNotFound());

        assertThat(bookRepository.findById(victimBook.getId()).orElseThrow().getStatus())
                .isEqualTo(BookStatus.WANT_TO_READ);
    }

    // ── 12. changeStatus 없는 bookId → 404 ──────────────────────────────────

    @Test
    @DisplayName("POST /api/books/{id}/status: 존재하지 않는 id → 404")
    void changeStatus_notFound_returns404() throws Exception {
        register("nf@a.com", "notfounduser", "없는책");
        mockMvc.perform(post("/api/books/{id}/status", 99999999L)
                        .with(user("nf@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READING\"}"))
                .andExpect(status().isNotFound());
    }

    // ── 13. setVisibility 정상 + PRIVATE opt-in 불변식 ───────────────────────

    @Test
    @DisplayName("POST /api/books/{id}/visibility: 공개 → 200 + isPublic·visibility·visibilityLabel + DB. 기본 PRIVATE opt-in")
    void setVisibility_returnsUpdated_and_defaultPrivate() throws Exception {
        User me = register("vis@a.com", "visusr", "공개자");
        Book book = addBook(me, "비공개책", null, BookStatus.READING);

        assertThat(book.isPublic()).isFalse(); // 기본 PRIVATE

        mockMvc.perform(post("/api/books/{id}/visibility", book.getId())
                        .with(user("vis@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic", is(true)))
                .andExpect(jsonPath("$.visibility", is("PUBLIC")))
                .andExpect(jsonPath("$.visibilityLabel", is("공개")));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().isPublic()).isTrue();
    }

    // ── 14. setVisibility IDOR → 404 ────────────────────────────────────────

    @Test
    @DisplayName("POST /api/books/{id}/visibility: 남의 책 → 404, 남의 책 visibility 불변 (IDOR)")
    void setVisibility_idor_returns404() throws Exception {
        User attacker = register("att2@a.com", "attacker14", "공격자2");
        User victim = register("vic2@a.com", "victim14", "피해자2");
        Book victimBook = addBook(victim, "피해자책", null, BookStatus.READING);

        mockMvc.perform(post("/api/books/{id}/visibility", victimBook.getId())
                        .with(user("att2@a.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isNotFound());

        assertThat(bookRepository.findById(victimBook.getId()).orElseThrow().isPublic()).isFalse();
    }

    // ── 15. delete 정상 → 200 deleted=true + DB 소멸 ────────────────────────

    @Test
    @DisplayName("POST /api/books/{id}/delete: 삭제 → 200 deleted=true + DB에서 사라짐")
    void delete_deletesBook() throws Exception {
        User me = register("del@a.com", "delusr", "삭제자");
        Book book = addBook(me, "삭제할책", null, BookStatus.READING);

        mockMvc.perform(post("/api/books/{id}/delete", book.getId())
                        .with(user("del@a.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", is(true)));

        assertThat(bookRepository.findById(book.getId())).isEmpty();
    }

    // ── 16. delete FK 정리 — reading_session.book_id → null (T-023 보존) ────

    @Test
    @DisplayName("POST /api/books/{id}/delete: reading_session 달린 책 삭제 → 세션 book_id=null 보존")
    void delete_unlinksReadingSessions() throws Exception {
        User me = register("fk@a.com", "fkusr", "FK자");
        Book book = addBook(me, "FK책", null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(me, Instant.now(), book);
        session.end(Instant.now().plusSeconds(60));
        ReadingSession saved = sessionRepository.save(session);

        mockMvc.perform(post("/api/books/{id}/delete", book.getId())
                        .with(user("fk@a.com")).with(csrf()))
                .andExpect(status().isOk());

        assertThat(sessionRepository.findById(saved.getId()).orElseThrow().getBook()).isNull();
        assertThat(bookRepository.findById(book.getId())).isEmpty();
    }

    // ── 17. delete IDOR → 404 + 책 잔존 ─────────────────────────────────────

    @Test
    @DisplayName("POST /api/books/{id}/delete: 남의 책 삭제 시도 → 404, 책 잔존 (IDOR)")
    void delete_idor_returns404() throws Exception {
        User attacker = register("att3@a.com", "attacker17", "공격자3");
        User victim = register("vic3@a.com", "victim17", "피해자3");
        Book victimBook = addBook(victim, "피해자책", null, BookStatus.READING);

        mockMvc.perform(post("/api/books/{id}/delete", victimBook.getId())
                        .with(user("att3@a.com")).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(bookRepository.findById(victimBook.getId())).isPresent();
    }
}
