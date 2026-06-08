package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.personality.PersonalityNarration;
import com.booktimer.personality.ReadingPersonalityNarrator;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 책BTI 본인 화면 컨트롤러 통합 테스트(Phase 5) — 인증 게이트 + 3상태 라우팅 + "다시 분석"(force) 배선.
 *
 * <p>상태 분류 로직은 {@link PersonalityViewTest}(단위)가, 캐시/재생성은 서비스 테스트가 본다. 여기선
 * 인증→유저→분석→뷰 와이어링과, 세 상태가 옳은 뷰 모델로 실리는지를 확인한다. LLM은 가짜로 끼운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalityControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private Clock clock;

    @MockitoBean
    private ReadingPersonalityNarrator narrator;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독자", SEOUL, Role.USER, today());
    }

    /** 책BTI는 공개(PUBLIC)+완독 책만으로 뽑히므로 픽스처는 공개+완독으로 적재한다. */
    private void saveBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            Book b = Book.register(u, "책" + i, "저자" + i, null, null, null, null,
                    null, null, BookStatus.FINISHED);
            b.makePublic();
            bookRepository.save(b);
        }
    }

    private PersonalityView viewOf(MvcResult result) {
        return (PersonalityView) result.getModelAndView().getModel().get("view");
    }

    @Test
    @DisplayName("GET /personality: 미인증이면 로그인으로 리다이렉트(보호된 페이지)")
    void get_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/personality"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /personality: 책 충분 + 서술 생성되면 READY로 서술을 싣는다")
    void get_ready_rendersNarrative() throws Exception {
        User u = register("ready@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("완독러 독자다.", List.of("완독러"))));

        MvcResult result = mockMvc.perform(get("/personality").with(user("ready@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("personality"))
                .andReturn();

        assertThat(viewOf(result).isReady()).isTrue();
        assertThat(viewOf(result).narrative()).isEqualTo("완독러 독자다.");
    }

    @Test
    @DisplayName("GET /personality: 완독 책이 0권(임계 미만)이면 COLD_START")
    void get_coldStart_whenNoFinishedBooks() throws Exception {
        User u = register("cold@booktimer.com");
        saveBooks(u, 0); // 완독 0권 — 임계(1) 미만

        MvcResult result = mockMvc.perform(get("/personality").with(user("cold@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(viewOf(result).isColdStart()).isTrue();
    }

    @Test
    @DisplayName("GET /personality: 책 충분하나 LLM 실패면 FALLBACK")
    void get_fallback_whenLlmEmpty() throws Exception {
        User u = register("fb@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(get("/personality").with(user("fb@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(viewOf(result).isFallback()).isTrue();
    }

    @Test
    @DisplayName("POST /personality/refresh: 강제 재생성하고 /personality로 리다이렉트(CSRF 필요)")
    void refresh_forcesAndRedirects() throws Exception {
        User u = register("refresh@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        mockMvc.perform(post("/personality/refresh").with(user("refresh@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personality"));

        verify(narrator, atLeastOnce()).narrate(any()); // 재생성 호출됨
    }

    @Test
    @DisplayName("POST /personality/refresh: CSRF 없으면 403")
    void refresh_withoutCsrf_forbidden() throws Exception {
        User u = register("nocsrf@booktimer.com");
        saveBooks(u, 5);

        mockMvc.perform(post("/personality/refresh").with(user("nocsrf@booktimer.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /personality/refresh: 하루 3번까지만 강제 재생성하고, 4번째부턴 LLM 호출 없이 차단(안내 플래그)")
    void refresh_dailyLimit_blocksAfterThree() throws Exception {
        User u = register("limit@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/personality/refresh").with(user("limit@booktimer.com")).with(csrf()))
                    .andExpect(redirectedUrl("/personality"));
        }
        // 4번째 — 한도 초과: LLM 재호출 없이 같은 페이지로 리다이렉트 + 안내 플래그
        mockMvc.perform(post("/personality/refresh").with(user("limit@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/personality"))
                .andExpect(flash().attribute("refreshLimited", true));

        verify(narrator, times(3)).narrate(any()); // 4번째는 호출되지 않음(딱 3번)
    }
}
