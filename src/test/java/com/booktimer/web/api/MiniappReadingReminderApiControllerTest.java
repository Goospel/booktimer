package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.ReadingReminderKind;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 미니앱 독서 알림 설정 저장 — {@code POST /api/miniapp/reading-reminder}(N3). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappReadingReminderApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired Clock clock;

    private User user(String email, String tossKey) {
        User u = registrationService.registerOAuth(email, "토스유저", SEOUL, AuthProvider.TOSS,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)), false);
        if (tossKey != null) {
            u.linkTossUserKey(tossKey);
            userRepository.save(u);
        }
        return u;
    }

    private ResultActions save(User u, String body) throws Exception {
        return mockMvc.perform(post("/api/miniapp/reading-reminder")
                .header("Authorization", "Bearer " + apiTokenService.issue(u))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("REQ-04 · 저장하면 200과 저장된 설정을 돌려주고 DB에 켠 시각이 박힌다")
    void save_returnsViewAndStampsOnAt() throws Exception {
        User u = user("rr-ok@noreply.booktimer.app", "tk-rr-ok");

        save(u, "{\"kind\":\"REST\",\"hour\":21}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("REST"))
                .andExpect(jsonPath("$.hour").value(21))
                .andExpect(jsonPath("$.everOn").value(true));

        User reloaded = userRepository.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getReadingReminderKind()).isEqualTo(ReadingReminderKind.REST);
        assertThat(reloaded.getReadingReminderHour()).isEqualTo(21);
        assertThat(reloaded.getReadingReminderOnAt()).isNotNull();
    }

    @Test
    @DisplayName("REQ-04 · 범위 밖 시각·모르는 방식·방식이 없는 요청은 400 평문")
    void save_invalid_400PlainText() throws Exception {
        User u = user("rr-bad@noreply.booktimer.app", "tk-rr-bad");

        for (String body : new String[] {
                "{\"kind\":\"DAILY\",\"hour\":7}",
                "{\"kind\":\"DAILY\",\"hour\":23}",
                "{\"kind\":\"WEEKLY\",\"hour\":20}",
                "{\"hour\":20}" }) {
            save(u, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string("알림 설정 값이 올바르지 않아요."))
                    .andExpect(content().string(not(startsWith("<"))));
        }
        assertThat(userRepository.findById(u.getId()).orElseThrow().getReadingReminderKind())
                .isEqualTo(ReadingReminderKind.OFF);
    }

    @Test
    @DisplayName("REQ-04 · 토스 미연결 계정은 409 평문")
    void save_notLinked_409() throws Exception {
        User u = user("rr-nolink@noreply.booktimer.app", null);

        save(u, "{\"kind\":\"DAILY\",\"hour\":20}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("토스 앱에 연결되지 않은 계정입니다"));
    }
}
