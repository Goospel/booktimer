package com.booktimer.web;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 양옆 세로 바 배선 종단 검증 — advice → 모델 {@code rail} → fragment → 페이지.
 *
 * <p>경로 판정 자체는 {@link RailNavTest}가 본다. 여기선 서버가 활성·모드·내 책방 가드를
 * 실제 마크업으로 내보내는지(양성·음성 짝)와, advice가 바 없는 화면·REST에서 터지지 않는지를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RailModelAdviceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRegistrationService registrationService;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private void registerUser(String email, String loginId, Role role) {
        registrationService.register(email, "rawpw1234!!", loginId, "닉_" + loginId, SEOUL, role, today());
    }

    /** 홈(/)은 온보딩 미완이면 /onboarding으로 302 — 홈 마크업을 보려면 완료 플래그가 필요하다. */
    private void registerOnboardedUser(String email, String loginId) {
        User user = registrationService.register(email, "rawpw1234!!", loginId, "닉_" + loginId, SEOUL, Role.USER, today());
        user.completeOnboarding();
        userRepository.save(user);
    }

    private String html(String path, String email) throws Exception {
        return mockMvc.perform(get(path).with(user(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 바 안의 링크 href → aria-current="page" 여부(문서 순서). 바가 없으면 빈 맵. */
    private static Map<String, Boolean> railLinks(String html) {
        Map<String, Boolean> links = new LinkedHashMap<>();
        int start = html.indexOf("id=\"side-rails\"");
        if (start < 0) return links;
        Matcher m = Pattern.compile("<a\\b[^>]*class=\"rail-item\"[^>]*>").matcher(html.substring(start));
        while (m.find()) {
            String tag = m.group();
            Matcher href = Pattern.compile("href=\"([^\"]*)\"").matcher(tag);
            assertThat(href.find()).as("rail-item에 href가 있어야 한다: %s", tag).isTrue();
            links.put(href.group(1), tag.contains("aria-current=\"page\""));
        }
        return links;
    }

    @Test
    @DisplayName("/books — 독서 모드, 내 책장만 활성, 내 책방 링크는 /u/{loginId}")
    void books_readingModeAndActive() throws Exception {
        registerUser("alice@booktimer.com", "alice", Role.USER);

        String body = html("/books", "alice@booktimer.com");

        assertThat(body).contains("id=\"side-rails\"").contains("data-mode=\"reading\"");
        assertThat(body).as("독서 페이지를 들렀으면 홈은 독서 타이머로 열린다").contains("data-remember=\"reading\"");
        Map<String, Boolean> links = railLinks(body);
        assertThat(links.keySet()).containsExactly(
                "/books", "/u/alice", "/personality", "/history", "/search",
                "/study", "/study/recall", "/study/books", "/study/history");
        assertThat(links.get("/books")).isTrue();
        assertThat(links.get("/history")).isFalse();
        assertThat(links.values().stream().filter(b -> b)).hasSize(1);
    }

    @Test
    @DisplayName("/study/history — 공부 모드, 공부 기록만 활성")
    void studyHistory_studyModeAndActive() throws Exception {
        registerUser("alice@booktimer.com", "alice", Role.USER);

        String body = html("/study/history", "alice@booktimer.com");

        assertThat(body).contains("data-mode=\"study\"").contains("data-remember=\"study\"");
        Map<String, Boolean> links = railLinks(body);
        assertThat(links.get("/study/history")).isTrue();
        assertThat(links.values().stream().filter(b -> b)).hasSize(1);
    }

    @Test
    @DisplayName("/study/recall — 공부 모드, 백지노트만 활성")
    void studyRecall_studyModeAndActive() throws Exception {
        registerUser("alice@booktimer.com", "alice", Role.USER);

        String body = html("/study/recall", "alice@booktimer.com");

        assertThat(body).contains("data-mode=\"study\"").contains("data-remember=\"study\"");
        Map<String, Boolean> links = railLinks(body);
        assertThat(links.get("/study/recall")).isTrue();
        assertThat(links.values().stream().filter(b -> b)).hasSize(1);
    }

    @Test
    @DisplayName("홈 / — 바에 활성 없음·data-remember 없음, 로고가 aria-current=\"page\"(로고가 홈이다)")
    void home_noRailActive_logoCurrent() throws Exception {
        registerOnboardedUser("alice@booktimer.com", "alice");

        String body = html("/", "alice@booktimer.com");

        assertThat(body).contains("id=\"side-rails\"");
        Map<String, Boolean> links = railLinks(body);
        assertThat(links).as("바에 홈 링크가 없다 — 로고가 홈이다").doesNotContainKey("/");
        assertThat(links.values()).as("홈 항목이 없으니 바에 활성이 없다").doesNotContain(true);
        assertThat(body).as("홈은 Vue 토글이 주인 — 저장값을 건드리지 않는다").doesNotContain("data-remember");
        Matcher logo = Pattern.compile("<a\\b[^>]*class=\"brand-home\"[^>]*>").matcher(body);
        assertThat(logo.find()).as("로고 링크가 있어야 한다").isTrue();
        assertThat(logo.group()).contains("aria-current=\"page\"");
    }

    @Test
    @DisplayName("/settings — 중립 페이지는 홈이 열릴 모드를 안 건드린다(공부 모드가 독서로 튀면 안 된다)")
    void settings_noRemember() throws Exception {
        registerUser("alice@booktimer.com", "alice", Role.USER);

        String body = html("/settings", "alice@booktimer.com");

        assertThat(body).contains("id=\"side-rails\"");
        assertThat(body).doesNotContain("data-remember");
    }

    @Test
    @DisplayName("온보딩 전 OAuth 사용자(loginId null) — 바는 뜨되 /u/ 링크가 없다")
    void oauthUserWithoutLoginId_noShopLink() throws Exception {
        registrationService.registerOAuth("oauth@booktimer.com", "소셜", SEOUL, AuthProvider.GOOGLE, today());

        String body = html("/books", "oauth@booktimer.com");

        assertThat(body).contains("id=\"side-rails\"");
        assertThat(railLinks(body)).containsKey("/books");
        assertThat(body).doesNotContain("href=\"/u/");
    }

    @Test
    @DisplayName("ADMIN — 책방이 404라 /u/ 링크가 없다")
    void admin_noShopLink() throws Exception {
        registerUser("chief@booktimer.com", "chief", Role.ADMIN);

        String body = mockMvc.perform(get("/books").with(user("chief@booktimer.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("id=\"side-rails\"");
        assertThat(body).doesNotContain("href=\"/u/");
    }

    @Test
    @DisplayName("남의 책방 /u/bob — 활성 메뉴 없음")
    void othersShop_noActive() throws Exception {
        registerUser("alice@booktimer.com", "alice", Role.USER);
        registerUser("bob@booktimer.com", "bob", Role.USER);

        String body = html("/u/bob", "alice@booktimer.com");

        assertThat(body).contains("id=\"side-rails\"");
        assertThat(body).doesNotContain("aria-current=\"page\"");
        assertThat(body).doesNotContain("data-remember");
    }

    @Test
    @DisplayName("/me/blocks — 바는 있고 활성 메뉴 없음")
    void blocks_railWithoutActive() throws Exception {
        registerUser("alice@booktimer.com", "alice", Role.USER);

        String body = html("/me/blocks", "alice@booktimer.com");

        assertThat(body).contains("id=\"side-rails\"");
        assertThat(body).doesNotContain("aria-current=\"page\"");
        assertThat(body).doesNotContain("data-remember");
    }

    @Test
    @DisplayName("익명 /login · ADMIN /admin — 바가 없다")
    void loginAndAdmin_noRail() throws Exception {
        String login = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(login).doesNotContain("side-rails");

        registerUser("chief@booktimer.com", "chief", Role.ADMIN);
        String admin = mockMvc.perform(get("/admin").with(user("chief@booktimer.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(admin).doesNotContain("side-rails");
    }

    @Test
    @DisplayName("/settings — 스킵 링크가 바보다 먼저 나오고, href가 그 페이지에 실제로 있는 도착 지점을 가리킨다")
    void skipLinkPrecedesRailAndResolves() throws Exception {
        // 템플릿 소스 가드(SideRailsTemplateTest)는 fragment와 페이지를 따로 본다 — 렌더 뒤 실제로 이어지는지는 여기서 본다.
        registerUser("alice@booktimer.com", "alice", Role.USER);

        String body = html("/settings", "alice@booktimer.com");

        int skip = body.indexOf("href=\"#main-content\"");
        assertThat(skip).as("렌더된 응답에 스킵 링크가 있어야 한다").isGreaterThanOrEqualTo(0);
        assertThat(skip).as("스킵 링크가 바보다 앞이어야 첫 Tab에 잡힌다").isLessThan(body.indexOf("id=\"side-rails\""));
        assertThat(body).as("앵커가 갈 곳 — 없으면 링크가 아무 일도 안 한다").contains("id=\"main-content\"");
        assertThat(body).as("도착 지점이 포커스를 받아야 한다").containsPattern("id=\"main-content\"[^>]*tabindex=\"-1\"");
    }

    @Test
    @DisplayName("REST /api/dashboard — advice가 JSON 응답에서 터지지 않는다")
    void api_notBrokenByAdvice() throws Exception {
        registerUser("alice@booktimer.com", "alice", Role.USER);

        mockMvc.perform(get("/api/dashboard").with(user("alice@booktimer.com")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("지연 해석 — 사용자를 안 읽는 화면은 advice가 DB 조회를 하지 않는다(해석 불가 principal도 200)")
    void userResolvedLazily() throws Exception {
        // /admin/users는 CurrentUserService를 안 쓰고 바도 없다. advice가 즉시 resolve()하면
        // DB에 없는 principal에서 AuthenticatedUserNotFoundException(500)이 난다 — 200이면 지연이다.
        mockMvc.perform(get("/admin/users").with(user("ghost-not-in-db").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
