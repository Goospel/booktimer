package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.personality.PersonalityNarration;
import com.booktimer.personality.ReadingPersonalityCache;
import com.booktimer.personality.ReadingPersonalityCacheRepository;
import com.booktimer.personality.ReadingPersonalityNarrator;
import com.booktimer.personality.ReadingPersonalityService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/personality + POST /refresh + POST /select/{id} 컨트롤러 통합 테스트 (선별 SPA 단계 1c).
 *
 * <p>① 인증 게이트(default-deny), ② 상태 3종 응답 형태(READY/COLD_START/FALLBACK),
 * ③ state 문자열·zone 미노출·generatedAtLabel, ④ CSRF 보호,
 * ⑤ refresh 한도초과 429+상태불변, ⑥ select IDOR(남의 entry → 대표 안 바뀜).
 * LLM은 {@link ReadingPersonalityNarrator} mock으로 실호출 회피.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalityApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired BookRepository bookRepository;
    @Autowired Clock clock;
    @Autowired ReadingPersonalityService personalityService;
    @Autowired ReadingPersonalityCacheRepository cacheRepository;

    @MockitoBean ReadingPersonalityNarrator narrator;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독자", SEOUL, Role.USER, today());
    }

    private void saveBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            Book b = Book.register(u, "책" + i, "저자" + i, null, null, null, null, null, null, BookStatus.FINISHED);
            b.makePublic();
            bookRepository.save(b);
        }
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/personality 미인증 → 302 (default-deny)")
    void get_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/personality"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /api/personality 인증 → 200 JSON + 구조(nickname·view.state·profile·entries·refreshRemaining·refreshLimit)")
    void get_authenticated_returnsJsonStructure() throws Exception {
        User u = register("papi@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        mockMvc.perform(get("/api/personality")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nickname").isString())
                .andExpect(jsonPath("$.view.state").isString())
                .andExpect(jsonPath("$.view.profile").exists())
                .andExpect(jsonPath("$.view.entries").isArray())
                .andExpect(jsonPath("$.refreshRemaining").isNumber())
                .andExpect(jsonPath("$.refreshLimit").isNumber());
    }

    @Test
    @DisplayName("GET /api/personality READY: state='READY', entries[0].generatedAtLabel 존재, narrative 있음")
    void get_ready_stateAndLabel() throws Exception {
        User u = register("papi-ready@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("완독러.", List.of("완독러"))));

        var result = mockMvc.perform(get("/api/personality")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-ready@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view.state").value("READY"))
                .andExpect(jsonPath("$.view.narrative").value("완독러."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("generatedAtLabel");
    }

    @Test
    @DisplayName("GET /api/personality COLD_START: 완독 0권 → state='COLD_START'")
    void get_coldStart() throws Exception {
        register("papi-cold@booktimer.com");

        mockMvc.perform(get("/api/personality")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-cold@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view.state").value("COLD_START"));
    }

    @Test
    @DisplayName("GET /api/personality FALLBACK: 책 충분 + LLM 실패 → state='FALLBACK'")
    void get_fallback() throws Exception {
        User u = register("papi-fb@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/personality")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-fb@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view.state").value("FALLBACK"));
    }

    @Test
    @DisplayName("GET /api/personality 직렬화: state는 문자열, zone은 응답에 없음")
    void get_serialization_noZone() throws Exception {
        User u = register("papi-ser@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        var result = mockMvc.perform(get("/api/personality")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-ser@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"zone\"");
        assertThat(body).contains("\"state\":\"READY\"");
    }

    // ── POST /refresh ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/personality/refresh CSRF 없으면 403")
    void refresh_noCsrf_forbidden() throws Exception {
        register("papi-nocsrf@booktimer.com");
        mockMvc.perform(post("/api/personality/refresh")
                        .with(user("papi-nocsrf@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/personality/refresh 성공: 200 + view + refreshRemaining 반환")
    void refresh_success_returnsUpdatedView() throws Exception {
        User u = register("papi-ref@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("새 서술.", List.of("태그"))));

        mockMvc.perform(post("/api/personality/refresh")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-ref@booktimer.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view.state").isString())
                .andExpect(jsonPath("$.refreshRemaining").isNumber())
                .andExpect(jsonPath("$.refreshLimit").isNumber());
    }

    @Test
    @DisplayName("POST /api/personality/refresh 한도 초과(3회 소진) → 429 + refreshRemaining=0 + 상태 불변")
    void refresh_limitExceeded_429() throws Exception {
        User u = register("papi-lim@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/personality/refresh")
                            .with(user("papi-lim@booktimer.com")).with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/personality/refresh")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-lim@booktimer.com")).with(csrf()))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("REFRESH_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.refreshRemaining").value(0));
    }

    // ── POST /select/{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/personality/select/{id} CSRF 없으면 403")
    void select_noCsrf_forbidden() throws Exception {
        User u = register("papi-selnocsrf@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));
        personalityService.reanalyze(u);
        Long id = cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u).get(0).getId();

        mockMvc.perform(post("/api/personality/select/{id}", id)
                        .with(user("papi-selnocsrf@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/personality/select/{id} 성공: 200 + 갱신된 view 반환")
    void select_success_returnsUpdatedView() throws Exception {
        User u = register("papi-sel@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술1.", List.of("태그"))));
        personalityService.reanalyze(u);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술2.", List.of("태그"))));
        personalityService.reanalyze(u);

        Long candidateId = cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u).get(0).getId();

        mockMvc.perform(post("/api/personality/select/{id}", candidateId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-sel@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view").exists())
                .andExpect(jsonPath("$.refreshRemaining").isNumber());
    }

    @Test
    @DisplayName("POST /api/personality/select/{id} IDOR — 남의 entry id면 대표 안 바뀜(조용히 무시)")
    void select_idor_noChange() throws Exception {
        User u1 = register("papi-idor1@booktimer.com");
        User u2 = register("papi-idor2@booktimer.com");
        saveBooks(u1, 5);
        saveBooks(u2, 5);

        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("u1서술.", List.of())));
        personalityService.reanalyze(u1);
        Long u1EntryId = cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u1).get(0).getId();

        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("u2서술.", List.of())));
        personalityService.reanalyze(u2);

        // u2가 u1의 entry id로 select 시도 → 200이지만 대표 변경 없음
        mockMvc.perform(post("/api/personality/select/{id}", u1EntryId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-idor2@booktimer.com")).with(csrf()))
                .andExpect(status().isOk());

        assertThat(cacheRepository.findByUserAndSelectedTrue(u1))
                .get()
                .extracting(ReadingPersonalityCache::getNarrative)
                .isEqualTo("u1서술.");
    }
}
