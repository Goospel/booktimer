package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.AuthProvider;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Autowired ApiTokenService apiTokenService;

    @MockitoBean ReadingPersonalityNarrator narrator;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독자", SEOUL, Role.USER, today());
    }

    /** 미니앱 유입 계정 — Bearer 토큰 경로(=미니앱 stateless 체인)를 타는 쪽. */
    private User tossUser(String email) {
        return registrationService.registerOAuth(email, "토스유저", SEOUL, AuthProvider.TOSS, today(), false);
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

    // ── GET /status + POST /ad-refresh (미니앱 광고 관문, 설계 §3.1) ────────────

    @Test
    @DisplayName("GET /api/personality/status 미인증 → 302 (Bearer 없으면 웹 체인의 default-deny)")
    void status_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/api/personality/status"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /api/personality/status: {coldStart,hasSelected,adRefreshRemaining=10,adRefreshLimit=10} — 총량 기준 잔여")
    void status_returnsGateAndTotalLimit() throws Exception {
        User u = register("papi-st@booktimer.com");
        saveBooks(u, 5);

        mockMvc.perform(get("/api/personality/status")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-st@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coldStart").value(false))
                .andExpect(jsonPath("$.hasSelected").value(false))
                .andExpect(jsonPath("$.adRefreshRemaining").value(User.DAILY_PERSONALITY_TOTAL_LIMIT))
                .andExpect(jsonPath("$.adRefreshLimit").value(User.DAILY_PERSONALITY_TOTAL_LIMIT));
    }

    @Test
    @DisplayName("GET /api/personality/status: 완독 0권이면 coldStart=true — 클라가 광고 버튼을 안 그린다")
    void status_coldStart() throws Exception {
        register("papi-st-cold@booktimer.com");

        mockMvc.perform(get("/api/personality/status")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-st-cold@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coldStart").value(true));
    }

    @Test
    @DisplayName("GET /api/personality/status는 분석을 만들지 않는다 — LLM 미호출 + 히스토리 0행 (관문이 무력화되지 않는 근거)")
    void status_hasNoBootstrapSideEffect() throws Exception {
        User u = register("papi-st-pure@booktimer.com");
        saveBooks(u, 5); // 부트스트랩 조건(책 충분 + 히스토리 빔)을 일부러 만든다

        mockMvc.perform(get("/api/personality/status")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-st-pure@booktimer.com")))
                .andExpect(status().isOk());

        assertThat(cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u)).isEmpty();
        verifyNoInteractions(narrator);
    }

    @Test
    @DisplayName("GET /api/personality/status: ad-refresh 소비 후 adRefreshRemaining이 줄어든다")
    void status_reflectsAdRefreshConsumption() throws Exception {
        User u = register("papi-st-dec@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        mockMvc.perform(post("/api/personality/ad-refresh")
                        .with(user("papi-st-dec@booktimer.com")).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/personality/status")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-st-dec@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adRefreshRemaining").value(User.DAILY_PERSONALITY_TOTAL_LIMIT - 1))
                .andExpect(jsonPath("$.hasSelected").value(true)); // 첫 분석은 자동 대표
    }

    @Test
    @DisplayName("GET /status: 히스토리를 최신순 entries로 싣는다 — 미니앱 보관함이 두 서술을 나란히 비교하는 유일한 재료")
    void status_carriesHistoryEntries() throws Exception {
        // 사용자 타임존을 서울도 UTC도 아닌 곳으로 둔다 — 라벨을 서버 존으로 찍는 회귀가 이 테스트에 걸리게.
        User u = registrationService.register("papi-st-hist@booktimer.com", "rawpw1234", "독자",
                "America/New_York", Role.USER, today());
        saveBooks(u, 5);

        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("첫번째 서술.", List.of("태그1"))));
        personalityService.reanalyze(u); // 첫 분석 = 자동 대표
        saveBooks(u, 1); // 책장이 바뀌었다 → 첫 분석은 stale이 된다
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("두번째 서술.", List.of("태그2"))));
        personalityService.reanalyze(u); // 두번째 = 후보(대표 불변)

        Instant newestAt = personalityService.history(u).get(0).generatedAt();
        String expectedLabel = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(newestAt.atZone(ZoneId.of("America/New_York")));

        mockMvc.perform(get("/api/personality/status")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-st-hist@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                // 최신이 앞 — history()의 순서 그대로다(대표 먼저인 웹 displayEntries 재정렬을 가져오지 않는다)
                .andExpect(jsonPath("$.entries[0].narrative").value("두번째 서술."))
                .andExpect(jsonPath("$.entries[0].selected").value(false))
                .andExpect(jsonPath("$.entries[0].stale").value(false))
                .andExpect(jsonPath("$.entries[0].id").isNumber())
                .andExpect(jsonPath("$.entries[0].generatedAt").isString())
                .andExpect(jsonPath("$.entries[0].generatedAtLabel").value(expectedLabel))
                .andExpect(jsonPath("$.entries[1].narrative").value("첫번째 서술."))
                .andExpect(jsonPath("$.entries[1].selected").value(true))
                .andExpect(jsonPath("$.entries[1].stale").value(true));
    }

    @Test
    @DisplayName("GET /status: 분석이 없으면 entries는 빈 배열 — 보관함 손잡이가 숨는 근거")
    void status_emptyHistory_entriesEmpty() throws Exception {
        User u = register("papi-st-empty@booktimer.com");
        saveBooks(u, 5);

        mockMvc.perform(get("/api/personality/status")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-st-empty@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    @DisplayName("POST /ad-refresh: 웹 천장(3)을 다 쓴 뒤에도 200 — 광고 경로는 총량까지 이어진다")
    void adRefresh_continuesAfterWebLimitExhausted() throws Exception {
        User u = register("papi-ad-after@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        for (int i = 0; i < User.DAILY_PERSONALITY_REFRESH_LIMIT; i++) {
            mockMvc.perform(post("/api/personality/refresh")
                            .with(user("papi-ad-after@booktimer.com")).with(csrf()))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/personality/refresh")
                        .with(user("papi-ad-after@booktimer.com")).with(csrf()))
                .andExpect(status().is(429)); // 웹은 여기서 막힌다

        mockMvc.perform(post("/api/personality/ad-refresh")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-ad-after@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshLimit").value(User.DAILY_PERSONALITY_TOTAL_LIMIT))
                .andExpect(jsonPath("$.refreshRemaining").value(User.DAILY_PERSONALITY_TOTAL_LIMIT - 4));
    }

    @Test
    @DisplayName("POST /ad-refresh: 총량 10회를 채우면 11회째 429 + refreshLimit=10 + 잔여 0(상태 불변)")
    void adRefresh_totalLimitExceeded_429() throws Exception {
        User u = register("papi-ad-lim@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        for (int i = 0; i < User.DAILY_PERSONALITY_TOTAL_LIMIT; i++) {
            mockMvc.perform(post("/api/personality/ad-refresh")
                            .with(user("papi-ad-lim@booktimer.com")).with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/personality/ad-refresh")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-ad-lim@booktimer.com")).with(csrf()))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("REFRESH_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.refreshRemaining").value(0))
                .andExpect(jsonPath("$.refreshLimit").value(User.DAILY_PERSONALITY_TOTAL_LIMIT));

        mockMvc.perform(post("/api/personality/ad-refresh")
                        .with(user("papi-ad-lim@booktimer.com")).with(csrf()))
                .andExpect(status().is(429)); // 반복 거부(상태 안정)
    }

    @Test
    @DisplayName("POST /ad-refresh 2회가 웹 무광고 칸도 소진한다 — 카운터는 하나다(설계 §3.2 ①)")
    void adRefresh_sharesCounterWithWebPath() throws Exception {
        User u = register("papi-ad-share@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/personality/ad-refresh")
                            .with(user("papi-ad-share@booktimer.com")).with(csrf()))
                    .andExpect(status().isOk());
        }

        // 웹 GET이 주는 잔여는 천장 3 기준 — 광고 2회를 썼으니 1칸 남았다
        mockMvc.perform(get("/api/personality")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("papi-ad-share@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshRemaining").value(1))
                .andExpect(jsonPath("$.refreshLimit").value(User.DAILY_PERSONALITY_REFRESH_LIMIT));

        mockMvc.perform(post("/api/personality/refresh")
                        .with(user("papi-ad-share@booktimer.com")).with(csrf()))
                .andExpect(status().isOk()); // 마지막 웹 칸
        mockMvc.perform(post("/api/personality/refresh")
                        .with(user("papi-ad-share@booktimer.com")).with(csrf()))
                .andExpect(status().is(429)); // 웹은 소진
    }

    // ── Bearer(미니앱 stateless 체인) 회귀 가드 — 설계 §2 실측을 테스트로 고정 ──

    @Test
    @DisplayName("Bearer: GET /status 200 — /api/personality/**도 미니앱 체인이 잡는다")
    void status_withBearerToken_ok() throws Exception {
        User u = tossUser("papi-bearer-st@noreply.booktimer.app");
        String token = apiTokenService.issue(u);

        mockMvc.perform(get("/api/personality/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adRefreshLimit").value(User.DAILY_PERSONALITY_TOTAL_LIMIT));
    }

    @Test
    @DisplayName("Bearer: POST /ad-refresh는 CSRF 토큰 없이 200 — stateless 체인은 CSRF가 꺼져 있다")
    void adRefresh_withBearerToken_noCsrf_ok() throws Exception {
        User u = tossUser("papi-bearer-ad@noreply.booktimer.app");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));
        String token = apiTokenService.issue(u);

        mockMvc.perform(post("/api/personality/ad-refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view.state").value("READY"))
                .andExpect(jsonPath("$.view.entries").isArray());
    }

    @Test
    @DisplayName("Bearer: POST /select/{id}도 CSRF 없이 동작 — 미니앱의 대표 승격 체이닝 경로")
    void select_withBearerToken_ok() throws Exception {
        User u = tossUser("papi-bearer-sel@noreply.booktimer.app");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술1.", List.of())));
        personalityService.reanalyze(u);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술2.", List.of())));
        personalityService.reanalyze(u);
        Long candidateId = cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u).get(0).getId();
        String token = apiTokenService.issue(u);

        mockMvc.perform(post("/api/personality/select/{id}", candidateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(cacheRepository.findByUserAndSelectedTrue(u))
                .get()
                .extracting(ReadingPersonalityCache::getNarrative)
                .isEqualTo("서술2.");
    }

    @Test
    @DisplayName("Bearer 토큰이 무효면 401 — 미니앱 체인의 인증이 이 경로에도 걸린다")
    void status_invalidBearerToken_401() throws Exception {
        mockMvc.perform(get("/api/personality/status").header("Authorization", "Bearer 지어낸토큰"))
                .andExpect(status().isUnauthorized());
    }
}
