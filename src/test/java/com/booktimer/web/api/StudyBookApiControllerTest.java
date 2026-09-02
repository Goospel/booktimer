package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공부 서재 API 통합 테스트 (H2) — {@code /api/study/books}.
 *
 * <p>여기서 재는 핵심은 <b>격리</b>다: 공부 책과 독서 책이 서로의 서재에 실리지 않는다. 별도 테이블
 * ({@code study_book})이라 구조적으로 참이지만, 그 구조가 깨졌을 때 울릴 계측기를 남긴다
 * ({@code StudyApiControllerTest}의 세션·목표·일정 격리와 같은 규율, V78 주석의 원칙).
 *
 * <p>에러 계약은 독서 서재({@code /api/books/*})와 같은 모양이다 — IDOR·미존재는 404로 통일하고
 * (존재 비노출), 도메인 규칙 위반(음수 회독)은 400이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyBookApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired StudySessionRepository studySessionRepository;
    @Autowired Clock clock;

    private User register(String email, String loginId) {
        registrationService.register(email, "pw1234qwer!!", loginId, "닉네임_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /** 공부 책 추가 요청 본문 — 검색 결과 행이 그대로 돌아오는 모양(status·category·pubDate 없음). */
    private static String addBody(String title, String isbn13) {
        return """
                {"title":"%s","author":"저자","isbn13":%s,"coverUrl":"https://cover/x.jpg",
                 "publisher":"출판사","purchaseLink":"https://aladin/x"}
                """.formatted(title, isbn13 == null ? "null" : "\"" + isbn13 + "\"");
    }

    private void addStudyBook(String loginId, String title, String isbn13) throws Exception {
        mockMvc.perform(post("/api/study/books").with(user(loginId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(title, isbn13)))
                .andExpect(status().isOk());
    }

    // ── 인증 경계 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/study/books: 미인증 → 로그인으로 차단")
    void shelf_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(get("/api/study/books"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── ① 추가 → 목록 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST → GET /api/study/books: 추가한 책이 0독으로 목록에 실린다")
    void add_thenShelf_listsBookWithZeroReadCount() throws Exception {
        register("sb-add@a.com", "sbadd");

        mockMvc.perform(post("/api/study/books").with(user("sbadd")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("정보처리기사 실기", "9791100000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("정보처리기사 실기"))
                .andExpect(jsonPath("$.readCount").value(0));

        mockMvc.perform(get("/api/study/books").with(user("sbadd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchEnabled").isBoolean())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].title").value("정보처리기사 실기"))
                .andExpect(jsonPath("$.books[0].isbn13").value("9791100000001"))
                .andExpect(jsonPath("$.books[0].readCount").value(0))
                .andExpect(jsonPath("$.books[0].purchaseLink").value("https://aladin/x"));
    }

    // ── ② 격리 (핵심 요구) ───────────────────────────────────────────────────

    @Test
    @DisplayName("격리: 공부 책은 독서 서재(GET /api/books)에 실리지 않는다")
    void studyBookDoesNotLeakIntoReadingShelf() throws Exception {
        register("sb-iso1@a.com", "sbisoone");
        addStudyBook("sbisoone", "공부 책", "9791100000002");

        mockMvc.perform(get("/api/books").with(user("sbisoone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books").isEmpty());
    }

    /**
     * 격리의 <b>두 축을 한 번에</b> 잰다 — ① 내 독서 책이 공부 서재에 안 실리고(도메인 경계)
     * ② <b>남의 공부 책</b>도 안 실린다(소유자 스코프).
     *
     * <p>②가 없으면 목록 쿼리가 {@code findAll()}로 바뀌어도 전 스위트가 초록이다 — 테스트마다
     * 사용자가 하나뿐이면 「내 것만」과 「전부」가 같은 답을 내기 때문이다(리뷰 W-1 실측).
     */
    @Test
    @DisplayName("격리: 공부 서재는 내 독서 책도, 남의 공부 책도 싣지 않는다(도메인 경계 + 소유자 스코프)")
    void studyShelfCarriesOnlyMyStudyBooks() throws Exception {
        register("sb-iso2@a.com", "sbisotwo");
        register("sb-iso3@a.com", "sbisothree");

        mockMvc.perform(post("/api/books").with(user("sbisotwo")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"독서 책","author":"저자","isbn13":"9791100000003",
                                 "status":"READING"}
                                """))
                .andExpect(status().isOk());
        addStudyBook("sbisotwo", "내 공부 책", "9791100000010");
        addStudyBook("sbisothree", "남의 공부 책", "9791100000011");

        mockMvc.perform(get("/api/study/books").with(user("sbisotwo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].title").value("내 공부 책"));
    }

    // ── ③ 회독 경계 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/read-count: 0 → 1 → 2로 절대값이 반영된다")
    void readCount_setsAbsoluteValue() throws Exception {
        User u = register("sb-count@a.com", "sbcount");
        addStudyBook("sbcount", "회독 대상", "9791100000004");
        Long id = onlyBookId(u);

        setReadCount("sbcount", id, 1).andExpect(jsonPath("$.readCount").value(1));
        setReadCount("sbcount", id, 2).andExpect(jsonPath("$.readCount").value(2));

        mockMvc.perform(get("/api/study/books").with(user("sbcount")))
                .andExpect(jsonPath("$.books[0].readCount").value(2));
    }

    @Test
    @DisplayName("POST /{id}/read-count: 같은 값 재설정은 멱등 — 연타·재시도에 안전하다")
    void readCount_sameValueIsIdempotent() throws Exception {
        User u = register("sb-idem@a.com", "sbidem");
        addStudyBook("sbidem", "멱등 대상", "9791100000005");
        Long id = onlyBookId(u);

        setReadCount("sbidem", id, 3).andExpect(jsonPath("$.readCount").value(3));
        setReadCount("sbidem", id, 3).andExpect(jsonPath("$.readCount").value(3));
    }

    @Test
    @DisplayName("POST /{id}/read-count: 음수는 400 — 도메인 규칙이 문 앞에서 걸린다")
    void readCount_negative_isBadRequest() throws Exception {
        User u = register("sb-neg@a.com", "sbneg");
        addStudyBook("sbneg", "음수 방어", "9791100000006");
        Long id = onlyBookId(u);

        mockMvc.perform(post("/api/study/books/" + id + "/read-count").with(user("sbneg")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readCount\":-1}"))
                .andExpect(status().isBadRequest());

        // 거부된 요청이 값을 흔들지 않는다.
        mockMvc.perform(get("/api/study/books").with(user("sbneg")))
                .andExpect(jsonPath("$.books[0].readCount").value(0));
    }

    // ── ④ IDOR ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IDOR: 남의 공부 책은 회독 변경도 삭제도 404 — 존재 여부조차 노출하지 않는다")
    void otherUsersBook_isNotFound() throws Exception {
        User owner = register("sb-owner@a.com", "sbowner");
        register("sb-thief@a.com", "sbthief");
        addStudyBook("sbowner", "남의 책", "9791100000007");
        Long id = onlyBookId(owner);

        mockMvc.perform(post("/api/study/books/" + id + "/read-count").with(user("sbthief")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readCount\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/study/books/" + id + "/delete").with(user("sbthief")).with(csrf()))
                .andExpect(status().isNotFound());

        // 주인의 책은 손대지지 않았다.
        assertThat(studyBookRepository.findByUserOrderByCreatedAtDesc(owner))
                .singleElement()
                .extracting(StudyBook::getReadCount).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /{id}/read-count: 없는 id는 404")
    void missingBook_isNotFound() throws Exception {
        register("sb-missing@a.com", "sbmissing");

        mockMvc.perform(post("/api/study/books/999999/read-count").with(user("sbmissing")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readCount\":1}"))
                .andExpect(status().isNotFound());
    }

    // ── ⑤ isbn 멱등 ─────────────────────────────────────────────────────────

    /**
     * ⚠️ <b>하이픈 표기를 먼저 담는 순서가 이 테스트의 핵심</b>이다 — 알라딘 응답의 isbn13은 표기가 갈릴 수
     * 있는데, {@link com.booktimer.book.Isbn#normalize}가 <b>적재 시점</b>에 표기를 모으지 않으면 하이픈째
     * 저장되고, 다음에 같은 책을 하이픈 없이 담을 때 조회가 빗나가 <b>같은 책이 두 행으로 쪼개지며 회독 수가
     * 0으로 되돌아간다</b>.
     *
     * <p><b>순서를 뒤집으면 이 테스트는 공허해진다</b>: {@code StudyBookService.add}가 조회 키를 스스로
     * 정규화하므로, 먼저 담은 값이 이미 하이픈 없는 꼴이면 엔티티의 정규화는 no-op이라 걷어내도 초록이다
     * (리뷰 W-2가 지적한 사각이고, 처음 쓴 계측기가 정확히 그 순서라 돌연변이가 살아남았다).
     */
    @Test
    @DisplayName("같은 isbn 재추가: 하이픈 표기로 먼저 담아도 표기를 모아 한 행을 유지하고 회독수를 보존한다")
    void addSameIsbn_keepsExistingRowAndReadCount() throws Exception {
        User u = register("sb-dup@a.com", "sbdup");
        // ① 하이픈 표기로 먼저 담는다 — 적재 정규화가 없으면 여기서 하이픈째 저장된다.
        addStudyBook("sbdup", "중복 대상", "979-11-0000-0008");
        Long id = onlyBookId(u);
        setReadCount("sbdup", id, 4).andExpect(status().isOk());

        // ② 하이픈 없는 표기로 재추가 — 적재가 정규화됐어야 여기서 기존 행을 찾는다.
        mockMvc.perform(post("/api/study/books").with(user("sbdup")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("중복 대상", "9791100000008")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.readCount").value(4));

        // ③ 하이픈 표기로 한 번 더 — 조회 키 정규화도 함께 잠근다.
        mockMvc.perform(post("/api/study/books").with(user("sbdup")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("중복 대상", "979-11-0000-0008")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.readCount").value(4));

        mockMvc.perform(get("/api/study/books").with(user("sbdup")))
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].readCount").value(4))
                // 저장된 표기는 정규화된 한 형태다(적재 단일 통로 — 동일성 키가 쪼개지지 않는 근거).
                .andExpect(jsonPath("$.books[0].isbn13").value("9791100000008"));
    }

    // ── ⑥ isbn null ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isbn이 없는 책은 동일성 키가 없어 여러 권 허용된다(독서 addFromSearch와 같은 규약)")
    void addWithoutIsbn_allowsMultipleRows() throws Exception {
        register("sb-noisbn@a.com", "sbnoisbn");

        addStudyBook("sbnoisbn", "무ISBN 한 권", null);
        addStudyBook("sbnoisbn", "무ISBN 두 권", null);

        mockMvc.perform(get("/api/study/books").with(user("sbnoisbn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(2)));
    }

    // ── 삭제 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/delete: 내 책은 서재에서 사라진다")
    void delete_removesFromShelf() throws Exception {
        User u = register("sb-del@a.com", "sbdel");
        addStudyBook("sbdel", "지울 책", "9791100000009");
        Long id = onlyBookId(u);

        mockMvc.perform(post("/api/study/books/" + id + "/delete").with(user("sbdel")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        mockMvc.perform(get("/api/study/books").with(user("sbdel")))
                .andExpect(jsonPath("$.books").isEmpty());
    }

    /**
     * <b>RED가 FK 위반 500이었던 자리</b> — 세션이 가리키는 책을 지우려면 그 참조를 먼저 풀어야 한다
     * ({@code StudySessionRepository.unlinkBook}). 시간 기록은 <b>보존</b>한다: 책을 서재에서 빼도
     * 그날 공부한 시간(당일 합·달력)은 사라지면 안 된다(달력·합계는 book을 아예 안 본다).
     *
     * <p>mock은 FK를 모른다(T-023·T-029) — 실 H2 통합으로만 잡히는 부류다.
     */
    @Test
    @DisplayName("POST /{id}/delete: 세션이 붙은 책도 지워지고, 그 세션은 「책 미지정」으로 남는다")
    void delete_unlinksSessionsAndKeepsTime() throws Exception {
        User u = register("sb-delsess@a.com", "sbdelsess");
        addStudyBook("sbdelsess", "시간이 붙은 책", "9791100000020");
        Long id = onlyBookId(u);
        StudyBook book = studyBookRepository.findById(id).orElseThrow();
        StudySession session = StudySession.start(u, clock.instant().minusSeconds(3600), book);
        session.end(clock.instant().minusSeconds(1800));
        Long sessionId = studySessionRepository.saveAndFlush(session).getId();

        mockMvc.perform(post("/api/study/books/" + id + "/delete").with(user("sbdelsess")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        StudySession kept = studySessionRepository.findById(sessionId).orElseThrow();
        assertThat(kept.getBook()).as("책은 사라져도 잰 시간은 남는다").isNull();
        assertThat(kept.getDurationSeconds()).isEqualTo(1800);
    }

    /** 소유자 스코프 — 집계가 {@code sumSecondsByBook()}에서 사용자 조건을 잃으면 남의 시간이 내 칩에 뜬다. */
    @Test
    @DisplayName("totalSeconds: 서재 행에 내 누적 공부 시간만 실린다(남의 시간은 안 섞인다)")
    void shelf_carriesOnlyMyTotalSeconds() throws Exception {
        User mine = register("sb-secs@a.com", "sbsecs");
        User stranger = register("sb-secs2@a.com", "sbsecstwo");
        addStudyBook("sbsecs", "내 책", "9791100000021");
        addStudyBook("sbsecstwo", "남의 책", "9791100000022");
        studied(mine, onlyBookId(mine), 1800);
        studied(stranger, onlyBookId(stranger), 600);

        mockMvc.perform(get("/api/study/books").with(user("sbsecs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books", hasSize(1)))
                .andExpect(jsonPath("$.books[0].totalSeconds").value(1800));
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    /** 그 책으로 잰 완료 세션 한 건을 심는다. */
    private void studied(User user, Long bookId, long seconds) {
        StudyBook book = studyBookRepository.findById(bookId).orElseThrow();
        StudySession session = StudySession.start(user, clock.instant().minusSeconds(seconds * 2), book);
        session.end(session.getStartedAt().plusSeconds(seconds));
        studySessionRepository.saveAndFlush(session);
    }

    private Long onlyBookId(User user) {
        return studyBookRepository.findByUserOrderByCreatedAtDesc(user).get(0).getId();
    }

    private org.springframework.test.web.servlet.ResultActions setReadCount(String loginId, Long id, int n)
            throws Exception {
        return mockMvc.perform(post("/api/study/books/" + id + "/read-count").with(user(loginId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readCount\":" + n + "}"))
                .andExpect(status().isOk());
    }
}
