package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.security.RateLimitService;
import com.booktimer.study.StudyNote;
import com.booktimer.study.StudyNoteRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공부 필기 API 통합 테스트 (H2) — {@code /api/study/notes}.
 *
 * <p>이 파일이 겨누는 실패는 셋이다. ① <b>책 없는 필기가 만들어지는 것</b> — 책이 정리 축이자 채점
 * 기준의 연결고리라, book_id가 새면 그 필기는 영영 채점에 안 들어가는 조용한 누락이 된다.
 * ② <b>IDOR</b> — 백지복습과 달리 조회 키가 id라 남의 필기에 닿는 문이 실제로 존재한다(그쪽은 키가
 * (나, 날짜)라 자리 자체가 없었다). ③ <b>자동저장 충돌</b> — 두 탭이 같은 필기를 열면 마지막 쓰기가
 * 앞의 긴 필기를 조용히 덮는다. revision 비교가 그걸 409로 세운다.
 *
 * <p>409 테스트는 「409가 떴다」로 끝내지 않고 <b>본문이 첫 갱신 값 그대로인지</b>까지 잰다 —
 * 상태 코드만 재면 「덮어쓰고 409를 돌려주는」 구현도 통과한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyNoteApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired StudyNoteRepository noteRepository;
    @Autowired Clock clock;
    @Autowired EntityManager entityManager;
    @Autowired RateLimitService rateLimitService;

    /** 레이트리밋 상태는 인메모리라 롤백을 안 탄다 — 테스트 사이에 새 나가지 않게 비운다. */
    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private StudyBook book(User user, String title) {
        return studyBookRepository.saveAndFlush(
                StudyBook.register(user, title, "저자", null, null, null, null));
    }

    private static String createBody(Long bookId, String title, String body) {
        return """
                {"bookId":%s,"title":%s,"body":"%s"}
                """.formatted(bookId == null ? "null" : bookId,
                title == null ? "null" : "\"" + title + "\"", body);
    }

    private static String updateBody(String title, String body, int revision) {
        return """
                {"title":%s,"body":"%s","revision":%d}
                """.formatted(title == null ? "null" : "\"" + title + "\"", body, revision);
    }

    /** 필기 한 장을 만들고 id를 돌려준다 — 목록·갱신·삭제 테스트의 공통 준비. */
    private Long createNote(String loginId, Long bookId, String title, String body) throws Exception {
        String json = mockMvc.perform(post("/api/study/notes").with(user(loginId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(bookId, title, body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1"));
    }

    // ── 인증 경계 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/study/notes: 미인증 → 로그인으로 차단")
    void list_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(get("/api/study/notes").param("bookId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── ① 책 필수 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("생성: bookId가 없으면 400 — 책 없는 필기는 만들 수 없다(행 0)")
    void create_withoutBook_is400() throws Exception {
        register("notenobook");

        mockMvc.perform(post("/api/study/notes").with(user("notenobook")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(null, null, "책 없이 쓴 필기")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("책을 골라 주세요"));

        assertThat(noteRepository.count()).isZero();
    }

    @Test
    @DisplayName("생성: 남의 bookId는 404(존재 비노출) — 행 0")
    void create_withOthersBook_is404() throws Exception {
        User owner = register("noteowner");
        register("notethief");
        StudyBook ownersBook = book(owner, "주인의 책");

        mockMvc.perform(post("/api/study/notes").with(user("notethief")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownersBook.getId(), null, "남의 책에 거는 필기")))
                .andExpect(status().isNotFound());

        assertThat(noteRepository.count()).isZero();
    }

    // ── ①-b 생성 레이트리밋 ──────────────────────────────────────────────────

    /**
     * 자동저장이 id 배선을 놓치면 1.5초마다 새 행을 만든다(분당 40행) — 그 폭주의 상한.
     *
     * <p><b>상한 안쪽까지 함께 잰다</b>: 30장이 전부 200이어야 통과한다. 「31번째가 막힌다」만 재면
     * 전부 막는 구현도 초록이다.
     *
     * <p>본문까지 재는 이유는 409 테스트와 같다 — 429가 {@code error.html}로 오면 화면은 안내 대신
     * {@code <!DOCTYPE html>…}을 받는다.
     */
    @Test
    @DisplayName("생성: 시간당 30장까지는 200이고 31번째만 429 평문 — 자동저장 폭주의 상한")
    void create_beyondHourlyLimit_is429() throws Exception {
        User user = register("noterate");
        Long bookId = book(user, "책").getId();

        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(post("/api/study/notes").with(user("noterate")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(bookId, null, "필기 " + i)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/study/notes").with(user("noterate")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(bookId, null, "31번째 필기")))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("필기를 너무 자주 만들었습니다"));

        assertThat(noteRepository.count()).as("막힌 요청은 행을 만들지 않는다").isEqualTo(30);
    }

    // ── ② 입력 검증 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("생성: 빈 본문 400 / 본문 8001자 400 / 제목 201자 400")
    void create_invalidInput_is400() throws Exception {
        User user = register("noteinvalid");
        Long bookId = book(user, "책").getId();

        mockMvc.perform(post("/api/study/notes").with(user("noteinvalid")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(bookId, null, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("쓴 내용을 입력해 주세요"));

        mockMvc.perform(post("/api/study/notes").with(user("noteinvalid")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(bookId, null, "가".repeat(StudyNote.BODY_MAX + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("쓴 내용은 " + StudyNote.BODY_MAX + "자까지 쓸 수 있어요"));

        mockMvc.perform(post("/api/study/notes").with(user("noteinvalid")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(bookId, "제".repeat(StudyNote.TITLE_MAX + 1), "본문")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("제목은 " + StudyNote.TITLE_MAX + "자까지 쓸 수 있어요"));

        assertThat(noteRepository.count()).as("잘못된 요청은 행을 만들지 않는다").isZero();
    }

    // ── ③ 왕복 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("생성 → 조회 왕복: 공백 제목은 null, revision은 0에서 시작")
    void create_thenGet_roundTrips() throws Exception {
        User user = register("noteround");
        Long bookId = book(user, "책").getId();

        Long id = createNote("noteround", bookId, "   ", "함수는 입력을 받아 출력을 낸다");

        mockMvc.perform(get("/api/study/notes/" + id).with(user("noteround")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.body").value("함수는 입력을 받아 출력을 낸다"))
                .andExpect(jsonPath("$.revision").value(0));
    }

    // ── ④ IDOR 3문 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("IDOR: 남의 필기는 조회·갱신·삭제 전부 404이고 행은 그대로다")
    void othersNote_is404OnEveryDoor() throws Exception {
        User owner = register("noteidorowner");
        register("noteidorthief");
        Long bookId = book(owner, "주인의 책").getId();
        Long id = createNote("noteidorowner", bookId, "주인의 필기", "주인이 쓴 본문");

        mockMvc.perform(get("/api/study/notes/" + id).with(user("noteidorthief")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/study/notes/" + id).with(user("noteidorthief")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "훔쳐 쓴 본문", 0)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/study/notes/" + id + "/delete").with(user("noteidorthief")).with(csrf()))
                .andExpect(status().isNotFound());

        StudyNote kept = noteRepository.findById(id).orElseThrow();
        assertThat(kept.getBody()).isEqualTo("주인이 쓴 본문");
        assertThat(kept.getTitle()).isEqualTo("주인의 필기");
    }

    // ── ⑤ 자동저장 충돌 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("갱신: 낡은 revision은 409이고 본문은 첫 갱신 값 그대로다(마지막 쓰기 승리 금지)")
    void update_withStaleRevision_is409AndKeepsBody() throws Exception {
        User user = register("notestale");
        Long bookId = book(user, "책").getId();
        Long id = createNote("notestale", bookId, null, "처음 쓴 본문");

        mockMvc.perform(post("/api/study/notes/" + id).with(user("notestale")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "탭 A가 쓴 긴 본문", 0)))
                .andExpect(status().isOk());

        // 탭 B는 아직 revision 0을 들고 있다 — 여기서 덮어쓰면 A의 본문이 조용히 사라진다.
        mockMvc.perform(post("/api/study/notes/" + id).with(user("notestale")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "탭 B가 덮어쓴 본문", 0)))
                .andExpect(status().isConflict())
                .andExpect(content().string("다른 곳에서 고쳐진 필기예요 — 새로고침한 뒤 이어서 써 주세요"));

        assertThat(noteRepository.findById(id).orElseThrow().getBody())
                .as("409를 돌려주면서 덮어쓰면 안 된다")
                .isEqualTo("탭 A가 쓴 긴 본문");
    }

    @Test
    @DisplayName("갱신: 성공하면 revision이 1 오른다")
    void update_bumpsRevision() throws Exception {
        User user = register("noterev");
        Long bookId = book(user, "책").getId();
        Long id = createNote("noterev", bookId, null, "처음");

        mockMvc.perform(post("/api/study/notes/" + id).with(user("noterev")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("제목", "고친 본문", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.body").value("고친 본문"));
    }

    // ── ⑤-2 갱신의 입력 검증 — 400이지 404가 아니다 ─────────────────────────

    /**
     * 생성과 갱신이 <b>같은 규칙</b>({@code requireBody}·{@code optionalTitle})을 쓰는데 문마다 다른 답을
     * 주면, 화면은 「없는 필기」와 「너무 긴 본문」을 구분하지 못한다 — 자동저장이 1.5초마다 두드리는
     * 문이라 사용자는 무엇을 고쳐야 하는지 영영 못 읽는다.
     */
    @Test
    @DisplayName("갱신: 본문 8001자는 400이고 저장된 본문은 그대로다(404 아님)")
    void update_overBodyMax_is400() throws Exception {
        User user = register("noteupdlong");
        Long bookId = book(user, "책").getId();
        Long id = createNote("noteupdlong", bookId, null, "처음 쓴 본문");

        mockMvc.perform(post("/api/study/notes/" + id).with(user("noteupdlong")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "가".repeat(StudyNote.BODY_MAX + 1), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("쓴 내용은 " + StudyNote.BODY_MAX + "자까지 쓸 수 있어요"));

        assertThat(noteRepository.findById(id).orElseThrow().getBody()).isEqualTo("처음 쓴 본문");
    }

    @Test
    @DisplayName("갱신: 빈 본문은 400이고 저장된 본문은 그대로다(404 아님)")
    void update_blankBody_is400() throws Exception {
        User user = register("noteupdblank");
        Long bookId = book(user, "책").getId();
        Long id = createNote("noteupdblank", bookId, null, "처음 쓴 본문");

        mockMvc.perform(post("/api/study/notes/" + id).with(user("noteupdblank")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "   ", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("쓴 내용을 입력해 주세요"));

        assertThat(noteRepository.findById(id).orElseThrow().getBody()).isEqualTo("처음 쓴 본문");
    }

    @Test
    @DisplayName("갱신: 제목 201자는 400이다(404 아님)")
    void update_overTitleMax_is400() throws Exception {
        User user = register("noteupdtitle");
        Long bookId = book(user, "책").getId();
        Long id = createNote("noteupdtitle", bookId, null, "처음 쓴 본문");

        mockMvc.perform(post("/api/study/notes/" + id).with(user("noteupdtitle")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("제".repeat(StudyNote.TITLE_MAX + 1), "고친 본문", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("제목은 " + StudyNote.TITLE_MAX + "자까지 쓸 수 있어요"));
    }

    /**
     * 검증 400을 세우면서 <b>순서를 뒤집으면</b> 남의 필기가 400(길이 위반)을, 없는 필기가 404를 줘
     * 400/404 차이로 「그 id가 존재하는가」를 캐낼 수 있다. 소유권 404가 언제나 먼저다.
     */
    @Test
    @DisplayName("IDOR: 남의 필기에 길이 위반 본문을 보내도 404다(400으로 존재를 흘리지 않는다)")
    void update_othersNoteWithInvalidBody_is404NotLeakingExistence() throws Exception {
        User owner = register("noteorderowner");
        register("noteorderthief");
        Long bookId = book(owner, "주인의 책").getId();
        Long id = createNote("noteorderowner", bookId, null, "주인이 쓴 본문");

        String overlong = updateBody(null, "가".repeat(StudyNote.BODY_MAX + 1), 0);

        mockMvc.perform(post("/api/study/notes/" + id).with(user("noteorderthief")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(overlong))
                .andExpect(status().isNotFound());

        // 없는 필기도 같은 404 — 두 응답이 같아야 존재 여부가 새지 않는다.
        mockMvc.perform(post("/api/study/notes/" + (id + 99_999)).with(user("noteorderthief")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(overlong))
                .andExpect(status().isNotFound());
    }

    // ── ⑥ 목록 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("목록: 최근 고친 것이 앞이고 본문은 싣지 않는다(chars만)")
    void list_isNewestFirstAndOmitsBody() throws Exception {
        User user = register("notelist");
        Long bookId = book(user, "책").getId();
        Long first = createNote("notelist", bookId, "먼저 쓴 필기", "열두 글자짜리 본문");
        createNote("notelist", bookId, "나중 쓴 필기", "짧은 본문");

        // 먼저 쓴 쪽을 고쳐 updatedAt을 최신으로 만든다 — 정렬 축이 createdAt이면 여기서 순서가 뒤집힌다.
        mockMvc.perform(post("/api/study/notes/" + first).with(user("notelist")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("먼저 쓴 필기", "방금 고친 본문", 0)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/study/notes").param("bookId", String.valueOf(bookId))
                        .with(user("notelist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes", hasSize(2)))
                .andExpect(jsonPath("$.notes[0].title").value("먼저 쓴 필기"))
                .andExpect(jsonPath("$.notes[0].chars").value("방금 고친 본문".length()))
                .andExpect(jsonPath("$.notes[0].body").doesNotExist())
                .andExpect(jsonPath("$.notes[1].title").value("나중 쓴 필기"));
    }

    /**
     * {@code updated_at}이 같은 두 장의 순서가 미정의면 <b>요청마다 뒤바뀔 수 있다</b> — 정답지(PR-3)가
     * 이 순서로 상한에 걸릴 장을 고르므로, 화면이 「들어간다」고 보여준 장과 모델이 본 장이 어긋난다.
     * 그래서 동률은 {@code id DESC}로 못 박는다(나중에 만든 장이 앞).
     */
    @Test
    @DisplayName("목록: updated_at이 같으면 id가 큰 쪽이 앞이다(동률이 흔들리지 않는다)")
    void list_breaksUpdatedAtTieById() throws Exception {
        User user = register("notetie");
        Long bookId = book(user, "책").getId();
        Long first = createNote("notetie", bookId, "먼저 쓴 필기", "본문 하나");
        Long second = createNote("notetie", bookId, "나중 쓴 필기", "본문 둘");

        // 두 행의 updated_at을 같은 값으로 눌러 동률을 만든다 — 마이크로초라 자연 발생은 드물다.
        entityManager.flush();
        entityManager.createNativeQuery("update study_note set updated_at = '2026-09-10 00:00:00'")
                .executeUpdate();
        entityManager.clear();

        mockMvc.perform(get("/api/study/notes").param("bookId", String.valueOf(bookId))
                        .with(user("notetie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].id").value(Math.max(first, second)))
                .andExpect(jsonPath("$.notes[1].id").value(Math.min(first, second)));
    }

    @Test
    @DisplayName("목록: 남의 bookId는 404 — 남의 필기 목록이 새지 않는다")
    void list_withOthersBook_is404() throws Exception {
        User owner = register("notelistowner");
        register("notelistthief");
        Long bookId = book(owner, "주인의 책").getId();
        createNote("notelistowner", bookId, "주인의 필기", "본문");

        mockMvc.perform(get("/api/study/notes").param("bookId", String.valueOf(bookId))
                        .with(user("notelistthief")))
                .andExpect(status().isNotFound());
    }

    // ── ⑦ 삭제 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("삭제: 내 필기는 지워지고 목록에서 사라진다")
    void delete_removesNote() throws Exception {
        User user = register("notedel");
        Long bookId = book(user, "책").getId();
        Long id = createNote("notedel", bookId, null, "지울 필기");

        mockMvc.perform(post("/api/study/notes/" + id + "/delete").with(user("notedel")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        assertThat(noteRepository.findById(id)).isEmpty();
    }

    // ── ⑧ 채점 기준 (PR-3) ───────────────────────────────────────────────────
    //
    // 이 문의 존재 이유는 <b>조용한 누락 금지</b>다. 상한에 걸려 빠진 장을 화면이 말하려면 그 수를
    // 서버가 알려 줘야 한다 — `excluded`가 늘 비어 있는 구현이면 사용자는 자기 필기가 채점에서
    // 빠진 것을 영영 모른다.

    @Test
    @DisplayName("채점 기준: 상한(24,000자)을 넘긴 장은 excluded로 갈린다 — 장 단위로")
    void reference_reportsIncludedAndExcluded() throws Exception {
        User user = register("noteref");
        Long bookId = book(user, "정보처리기사 실기").getId();
        // 최근순이라 <b>마지막에 만든 장이 맨 앞</b>이다. 오래된 「여백」이 상한에 밀려 빠지는 것이
        // 이 문의 관심사 — 조용히 빠지면 사용자가 모른다.
        // 크기는 헤더까지 센다(NoteReference.chars = 모델이 받는 글자 수): 7800자 장이 7826, 셋이면
        // 23,478이라 남은 자리가 522뿐 — 8000자짜리 「여백」은 통째로 빠진다.
        String full = "ㄱ".repeat(7800);
        createNote("noteref", bookId, "여백", "ㄴ".repeat(8000));
        createNote("noteref", bookId, "1장", full);
        createNote("noteref", bookId, "2장", full);
        createNote("noteref", bookId, "3장", full);

        mockMvc.perform(get("/api/study/notes/reference").param("bookId", String.valueOf(bookId))
                        .with(user("noteref")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.included", hasSize(3)))
                .andExpect(jsonPath("$.excluded", hasSize(1)))
                .andExpect(jsonPath("$.included[0].title").value("3장"))
                .andExpect(jsonPath("$.excluded[0].title").value("여백"))
                .andExpect(jsonPath("$.chars").value(23_478))
                .andExpect(jsonPath("$.limit").value(24_000));
    }

    @Test
    @DisplayName("채점 기준: 필기가 없으면 빈 목록 — 카드가 「필기 없음」을 말할 근거")
    void reference_withoutNotes_isEmpty() throws Exception {
        User user = register("noterefempty");
        Long bookId = book(user, "책").getId();

        mockMvc.perform(get("/api/study/notes/reference").param("bookId", String.valueOf(bookId))
                        .with(user("noterefempty")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.included", hasSize(0)))
                .andExpect(jsonPath("$.excluded", hasSize(0)))
                .andExpect(jsonPath("$.chars").value(0));
    }

    @Test
    @DisplayName("채점 기준: 남의 bookId는 404 — 남의 필기 제목이 카드로 새지 않는다")
    void reference_withOthersBook_is404() throws Exception {
        User owner = register("noterefowner");
        register("noterefthief");
        Long bookId = book(owner, "주인의 책").getId();
        createNote("noterefowner", bookId, "주인의 필기", "본문");

        mockMvc.perform(get("/api/study/notes/reference").param("bookId", String.valueOf(bookId))
                        .with(user("noterefthief")))
                .andExpect(status().isNotFound());
    }

    // ── ⑨ 목록 라벨의 근거 (PR-3) ────────────────────────────────────────────

    /**
     * 제목을 안 적는 것이 이 기능의 <b>기본 사용법</b>이다(쓰는 대로 저장되는 필기라 제목 칸에 손이
     * 안 간다). 그래서 목록 응답에 본문 실마리가 없으면 화면은 거의 모든 행을 「제목 없음」으로 그린다 —
     * 설계 §3.7이 라벨을 {@code noteLabel(title, body)}로 뒀는데 §3.4의 목록엔 body가 없던 어긋남의
     * 근본 처방이다(§3.6의 「본문 없는 목록」은 그대로 — 첫 줄만 싣는다).
     */
    @Test
    @DisplayName("목록: 본문 첫 줄이 preview로 실린다 — 제목 없는 장이 「제목 없음」으로 뭉개지지 않게")
    void list_carriesPreviewOfFirstLine() throws Exception {
        User user = register("notepreview");
        Long bookId = book(user, "책").getId();
        createNote("notepreview", bookId, null, "# 미분계수\\n- 접선의 기울기");

        mockMvc.perform(get("/api/study/notes").param("bookId", String.valueOf(bookId))
                        .with(user("notepreview")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].preview").value("# 미분계수"))
                // 목록은 여전히 본문을 안 싣는다 — preview는 첫 줄뿐이다.
                .andExpect(jsonPath("$.notes[0].body").doesNotExist());
    }

    @Test
    @DisplayName("목록: 빈 줄로 시작하는 본문은 첫 <b>비공백</b> 줄이 preview다")
    void list_previewSkipsBlankLines() throws Exception {
        User user = register("notepreviewblank");
        Long bookId = book(user, "책").getId();
        createNote("notepreviewblank", bookId, null, "\\n   \\n두 번째 줄이 첫 글이다");

        mockMvc.perform(get("/api/study/notes").param("bookId", String.valueOf(bookId))
                        .with(user("notepreviewblank")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].preview").value("두 번째 줄이 첫 글이다"));
    }
}
