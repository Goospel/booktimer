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
import org.springframework.mock.web.MockHttpSession;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 책BTI 셸 컨트롤러 통합 테스트 (선별 SPA 단계 1c 이후).
 *
 * <p>GET /personality 인증 게이트·셸 렌더만 확인한다.
 * 뮤테이션(refresh·select) 테스트는 {@link com.booktimer.web.api.PersonalityApiControllerTest}로 이관됨.
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

    private void saveBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            Book b = Book.register(u, "책" + i, "저자" + i, null, null, null, null,
                    null, null, BookStatus.FINISHED);
            b.makePublic();
            bookRepository.save(b);
        }
    }

    @Test
    @DisplayName("GET /personality: 미인증이면 로그인으로 리다이렉트(보호된 페이지)")
    void get_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/personality"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /personality: 인증 시 personality 뷰 렌더(Vue 섬 셸)")
    void get_authenticated_rendersShell() throws Exception {
        register("shell@booktimer.com");

        mockMvc.perform(get("/personality").with(user("shell@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("personality"));
    }

    @Test
    @DisplayName("GET /personality: 개발용 파서 주석이 렌더 출력에 새지 않는다(#247 재발 방지)")
    void get_doesNotLeakDevComment() throws Exception {
        register("leak2@booktimer.com");
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));

        MvcResult result = mockMvc.perform(get("/personality")
                        .with(user("leak2@booktimer.com")).session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).doesNotContain("*/-->");
        assertThat(html).doesNotContain("응답 버퍼");
    }
}
