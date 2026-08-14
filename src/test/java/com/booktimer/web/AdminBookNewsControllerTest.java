package com.booktimer.web;

import com.booktimer.book.BookNewsCollector;
import com.booktimer.book.NewsCollectionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책 뉴스 수동 수집 컨트롤러 통합 테스트.
 *
 * <p>수집은 새벽 04:30 배치가 도는데, 새로 완독한 책의 뉴스를 그날 바로 보려면 손으로 돌릴 길이 필요하다
 * (배포 직후 첫 점등 확인도 같은 이유). 백필 액션과 같은 경계를 그대로 따른다 — /admin/** 은 ADMIN만,
 * 쓰기 액션이라 POST + CSRF, 일반 USER는 403.
 *
 * <p>수집 로직 자체(대상 선별·매칭·상위 3건 교체)는 {@code BookNewsCollectorTest}가 본다. 여기선
 * 인가 경계·라우팅·결과 플래시 배선만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBookNewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /admin/books/collect-news: ADMIN이면 실행하고 결과 플래시와 함께 /admin으로 리다이렉트")
    void collect_admin_runsAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/books/collect-news")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    @DisplayName("POST /admin/books/collect-news: 일반 사용자는 403 — 관리자 액션은 경계가 1순위")
    void collect_nonAdmin_forbidden() throws Exception {
        mockMvc.perform(post("/admin/books/collect-news")
                        .with(user("u@booktimer.com")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/books/collect-news: 로그인하지 않으면 실행되지 않는다")
    void collect_anonymous_notRun() throws Exception {
        mockMvc.perform(post("/admin/books/collect-news").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /*
     * 문구 두 갈래는 Spring 컨텍스트 없이 본다 — 꺼진 상태를 통합 테스트로 재현하려면 프로퍼티가 다른
     * 컨텍스트가 하나 더 떠서 전체 스위트가 느려진다. 여기서 재는 건 "결과를 어떤 말로 옮기는가"뿐이라
     * 스텁 수집기로 충분하다.
     */
    private static String messageOf(NewsCollectionResult result) {
        BookNewsCollector stub = new BookNewsCollector(null, null, null) {
            @Override
            public NewsCollectionResult collect() {
                return result;
            }
        };
        RedirectAttributes attributes = new RedirectAttributesModelMap();
        new AdminBookNewsController(stub).collect(attributes);
        return String.valueOf(attributes.getFlashAttributes().get("message"));
    }

    @Test
    @DisplayName("결과를 종수·건수로 옮긴다")
    void message_reportsCounts() {
        assertThat(messageOf(new NewsCollectionResult(true, 7, 12))).isEqualTo("책 뉴스 수집 — 대상 7종, 저장 12건");
    }

    /*
     * 꺼져 있을 때 "0종 0건"으로 말하면 관리자가 "완독한 책이 없나?"로 오해해 엉뚱한 곳을 뒤진다.
     * 이 갈래가 사라지면 killswitch를 내려 둔 채 원인을 못 찾는 시간이 그대로 비용이 된다.
     */
    @Test
    @DisplayName("꺼져 있으면 0종 0건이 아니라 꺼졌다고 말한다")
    void message_saysDisabled() {
        String message = messageOf(new NewsCollectionResult(false, 0, 0));

        assertThat(message).contains("꺼져 있습니다");
        assertThat(message).doesNotContain("0종");
    }
}
