package com.booktimer.web.api;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.study.StudyNote;
import com.booktimer.study.StudyNoteRepository;
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
}
