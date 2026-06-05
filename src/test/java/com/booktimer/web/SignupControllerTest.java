package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 회원가입 화면/처리 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>비로그인 상태에서 가입 화면이 공개되는지, 폼 제출이 검증을 거쳐 사용자를 영속화하고
 * 로그인으로 리다이렉트하는지, 입력 오류 시 화면을 다시 그리는지(영속화 없음) 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /signup: 비로그인도 가입 화면을 볼 수 있고 폼 모델이 실린다")
    void getSignup_isPublic() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeExists("signupForm"));
    }

    @Test
    @DisplayName("GET /signup: 타임존 선택지(드롭다운) 목록을 모델에 싣는다")
    void getSignup_includesTimezoneOptions() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("timezones", hasItem("Asia/Seoul")));
    }

    @Test
    @DisplayName("POST /signup: 유효 입력이면 login_id를 확정해 사용자를 만들고 로그인으로 리다이렉트한다")
    void postSignup_valid_persistsAndRedirects() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("email", "newuser@booktimer.com")
                        .param("loginId", "newuser1")
                        .param("password", "rawpw1234")
                        .param("nickname", "책벌레")
                        .param("timezone", "Asia/Seoul"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        User saved = userRepository.findByEmail("newuser@booktimer.com").orElseThrow();
        assertThat(saved.getLoginId()).isEqualTo("newuser1"); // 가입에서 로그인 식별자 확정
    }

    @Test
    @DisplayName("POST /signup: 이메일이 비면 화면을 다시 그리고 사용자를 만들지 않는다")
    void postSignup_invalid_rerendersWithoutPersisting() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("email", "")
                        .param("loginId", "someid")
                        .param("password", "rawpw1234")
                        .param("nickname", "책벌레")
                        .param("timezone", "Asia/Seoul"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "email"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /signup: 이미 가입된 이메일이면 화면을 다시 그리고 이메일 필드 에러를 단다 (500 아님, 중복 생성 없음)")
    void postSignup_duplicateEmail_rerendersWithFieldError() throws Exception {
        userRepository.save(User.of("dup@booktimer.com", "$2a$10$alreadyhasheddummy", "기존", "Asia/Seoul", Role.USER));

        mockMvc.perform(post("/signup").with(csrf())
                        .param("email", "dup@booktimer.com")
                        .param("loginId", "freshid")
                        .param("password", "rawpw1234")
                        .param("nickname", "새사람")
                        .param("timezone", "Asia/Seoul"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "email"));

        assertThat(userRepository.count()).isEqualTo(1);  // 기존 1명만, 중복 생성 없음
    }

    @Test
    @DisplayName("POST /signup: 이미 쓰이는 login_id면 화면을 다시 그리고 loginId 필드 에러를 단다 (중복 생성 없음)")
    void postSignup_duplicateLoginId_rerendersWithFieldError() throws Exception {
        User owner = User.of("owner@booktimer.com", "$2a$10$alreadyhasheddummy", "기존", "Asia/Seoul", Role.USER);
        owner.assignLoginId("grabbed");
        userRepository.save(owner);

        mockMvc.perform(post("/signup").with(csrf())
                        .param("email", "taker@booktimer.com")
                        .param("loginId", "grabbed")
                        .param("password", "rawpw1234")
                        .param("nickname", "새사람")
                        .param("timezone", "Asia/Seoul"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "loginId"));

        assertThat(userRepository.findByEmail("taker@booktimer.com")).isEmpty();  // 생성 안 됨
    }
}
