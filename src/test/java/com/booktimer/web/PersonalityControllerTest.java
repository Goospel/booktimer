package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.personality.PersonalityNarration;
import com.booktimer.personality.PublicReadingPersonalityCacheRepository;
import com.booktimer.personality.ReadingPersonalityNarrator;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PublicReadingPersonalityCacheRepository publicCacheRepository;

    @MockitoBean
    private ReadingPersonalityNarrator narrator;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "독자", SEOUL, Role.USER, today());
    }

    private void saveBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            bookRepository.save(Book.register(u, "책" + i, "저자" + i, null, null, null, null,
                    null, null, BookStatus.FINISHED));
        }
    }

    private void savePublicBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            Book b = Book.register(u, "공개책" + i, "저자" + i, null, null, null, null,
                    null, null, BookStatus.FINISHED);
            b.makePublic();
            bookRepository.save(b);
        }
    }

    private User reload(String email) {
        return userRepository.findByEmail(email).orElseThrow();
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
    @DisplayName("GET /personality: 책이 임계 미만이면 COLD_START")
    void get_coldStart_whenFewBooks() throws Exception {
        User u = register("cold@booktimer.com");
        saveBooks(u, 3);

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

    // --- POST /personality/visibility: 책방 노출 opt-in 토글 ---

    @Test
    @DisplayName("POST /personality/visibility public=true: 플래그를 켜고, 공개 책BTI를 생성한 뒤 /personality로 리다이렉트")
    void visibility_turnOn_setsFlagAndGeneratesPublicCache() throws Exception {
        User u = register("expose@booktimer.com");
        savePublicBooks(u, 5); // 공개+완독 5권 → 공개 책BTI 생성 가능(콜드스타트 아님)
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("공개 책 기준 서술.", List.of("태그"))));

        mockMvc.perform(post("/personality/visibility")
                        .param("public", "true")
                        .with(user("expose@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personality"));

        assertThat(reload("expose@booktimer.com").isPersonalityPublic()).isTrue();
        // 켤 때 공개 캐시를 즉시 생성해 둔다(방문자 조회는 읽기 전용이므로 소유자가 미리 만들어야)
        assertThat(publicCacheRepository.findByUser(u)).isPresent();
    }

    @Test
    @DisplayName("POST /personality/visibility public=false: 플래그를 끈다(노출 중단)")
    void visibility_turnOff_clearsFlag() throws Exception {
        User u = register("hide@booktimer.com");
        u.setPersonalityPublic(true);
        userRepository.save(u);

        mockMvc.perform(post("/personality/visibility")
                        .param("public", "false")
                        .with(user("hide@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/personality"));

        assertThat(reload("hide@booktimer.com").isPersonalityPublic()).isFalse();
    }

    @Test
    @DisplayName("POST /personality/visibility: CSRF 없으면 403 (상태 변경 보호)")
    void visibility_withoutCsrf_forbidden() throws Exception {
        register("novis@booktimer.com");

        mockMvc.perform(post("/personality/visibility")
                        .param("public", "true")
                        .with(user("novis@booktimer.com")))
                .andExpect(status().isForbidden());
    }
}
