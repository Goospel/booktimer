package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Content-Security-Policy — nonce 기반 strict CSP의 계약.
 *
 * <p>이 정책의 위험은 "너무 느슨해서 못 막는 것"이 아니라 <b>너무 조여서 우리 스크립트가 조용히 안 도는 것</b>이다.
 * nonce가 빠진 {@code <script>}는 에러 없이 그냥 실행되지 않아, 화면은 멀쩡한데 기능 하나만 죽는다.
 * 그래서 런타임 계약(헤더·nonce 일치)과 <b>템플릿 소스 스캔</b>을 함께 둔다 — 소스 스캔이 있어야
 * 이 테스트가 렌더해 보지 않은 나머지 화면까지 커버된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CspTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String HEADER = "Content-Security-Policy";

    /** {@code nonce-<값>} 에서 값만. 헤더와 렌더된 태그가 같은 값을 쓰는지 대조하는 데 쓴다. */
    private static final Pattern HEADER_NONCE = Pattern.compile("'nonce-([A-Za-z0-9_\\-]+)'");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    // ── 런타임 계약 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HTML 응답에 CSP 헤더가 붙는다")
    void htmlResponse_carriesCspHeader() throws Exception {
        String policy = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HEADER);

        assertThat(policy).isNotBlank();
    }

    @Test
    @DisplayName("정책이 XSS 방어의 핵심 지시자를 담는다")
    void policy_hasRequiredDirectives() throws Exception {
        String policy = policyOf("/login");

        assertThat(policy)
                // nonce + strict-dynamic — 이 둘이 있어야 최신 브라우저가 'unsafe-inline'·호스트 허용목록을
                // 무시하고 "nonce 받은 스크립트와 그것이 심은 것만" 실행한다(AdSense·GA가 이 위에서 산다).
                .contains("'strict-dynamic'")
                // 플러그인·<object> 벡터 차단
                .contains("object-src 'none'")
                // <base href="//evil"> 로 상대경로 스크립트·폼을 통째로 납치하는 것 차단
                .contains("base-uri 'self'")
                // XSS가 심은 <form action="//evil"> 로의 자격증명 유출 차단
                .contains("form-action 'self'")
                // 클릭재킹 차단(기존 X-Frame-Options DENY와 같은 정책을 CSP로도 명시)
                .contains("frame-ancestors 'none'");
        assertThat(HEADER_NONCE.matcher(policy).find()).isTrue();
    }

    @Test
    @DisplayName("nonce는 요청마다 새로 발급된다 (고정 nonce는 CSP를 통째로 무력화한다)")
    void nonce_differsPerRequest() throws Exception {
        assertThat(nonceOf("/login")).isNotEqualTo(nonceOf("/login"));
    }

    @Test
    @DisplayName("렌더된 페이지의 모든 <script> 태그가 '그 요청의' nonce를 달고 있다")
    void everyScriptTagInRenderedPage_carriesThisRequestsNonce() throws Exception {
        registrationService.register("csp-user@booktimer.com", "pw1234qwer!!", "cspuserid", "정책테스터", SEOUL, Role.USER, today());

        var response = mockMvc.perform(get("/books").with(user("csp-user@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String nonce = nonceIn(response.getHeader(HEADER));
        List<String> tags = scriptTags(stripComments(response.getContentAsString()));

        // 태그가 0개면 아래 단언이 공허하게 통과한다 — 이 페이지엔 섬 번들이 있어야 정상이다.
        assertThat(tags).isNotEmpty();
        assertThat(tags).allSatisfy(tag ->
                assertThat(tag).contains("nonce=\"" + nonce + "\""));
    }

    // ── 템플릿 소스 스캔 (렌더해 보지 않은 화면까지 커버) ──────────────────────

    @Test
    @DisplayName("모든 템플릿의 <script> 태그에 nonce 속성이 있다")
    void everyScriptTagInTemplates_declaresNonce() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Resource template : templates()) {
            for (String tag : scriptTags(stripComments(read(template)))) {
                if (!tag.contains("nonce")) {
                    offenders.add(template.getFilename() + " → " + tag.strip());
                }
            }
        }

        // strict-dynamic 아래에서는 'self' 같은 호스트 허용목록이 무시되므로, 외부 src 태그도 예외가 아니다.
        // nonce가 빠지면 그 스크립트만 조용히 안 돈다(콘솔 경고뿐, 화면은 멀쩡).
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("app.css가 @import 하는 외부 호스트를 style-src가 허용한다")
    void externalHostsImportedByStylesheet_areAllowed() throws Exception {
        // 본문 폰트를 app.css가 @import로 불러온다 — 템플릿엔 <link>가 없어서 HTML만 훑으면 안 보인다.
        // 실제로 이 사각 때문에 첫 정책이 폰트를 막았고, 단위테스트는 전부 통과한 채 실 브라우저에서 잡혔다.
        // 그래서 "HTML에 없는 외부 의존"을 스타일시트에서 직접 읽어 정책과 대조한다.
        String css = read(new PathMatchingResourcePatternResolver()
                .getResource("classpath:static/css/app.css"));
        String policy = policyOf("/login");

        Matcher m = Pattern.compile("https://[A-Za-z0-9.-]+").matcher(css);
        List<String> hosts = new ArrayList<>();
        while (m.find()) {
            if (!hosts.contains(m.group())) {
                hosts.add(m.group());
            }
        }

        assertThat(hosts).as("app.css에 외부 호스트가 하나는 있어야 이 테스트가 공허하지 않다").isNotEmpty();
        assertThat(hosts).allSatisfy(host -> assertThat(policy).contains(host));
    }

    @Test
    @DisplayName("템플릿에 인라인 이벤트 핸들러(onclick·onsubmit…)가 없다")
    void templates_haveNoInlineEventHandlers() throws Exception {
        // 인라인 핸들러는 nonce를 붙일 수 없어 CSP에서 실행되지 않는다('unsafe-hashes' 없이는 방법이 없다).
        // 남아 있으면 "확인 대화상자 없이 바로 삭제되는" 식으로 조용히 동작이 바뀐다.
        Pattern handler = Pattern.compile("\\son(click|submit|change|input|load|error|focus|blur)\\s*=");
        List<String> offenders = new ArrayList<>();
        for (Resource template : templates()) {
            Matcher m = handler.matcher(stripComments(read(template)));
            while (m.find()) {
                offenders.add(template.getFilename() + " → " + m.group().strip());
            }
        }

        assertThat(offenders).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String policyOf(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getHeader(HEADER);
    }

    private String nonceOf(String path) throws Exception {
        return nonceIn(policyOf(path));
    }

    private static String nonceIn(String policy) {
        Matcher m = HEADER_NONCE.matcher(policy == null ? "" : policy);
        assertThat(m.find()).as("정책에 nonce가 있어야 한다: %s", policy).isTrue();
        return m.group(1);
    }

    private static List<String> scriptTags(String html) {
        List<String> tags = new ArrayList<>();
        Matcher m = Pattern.compile("<script\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            tags.add(m.group());
        }
        return tags;
    }

    /** 주석 안의 예시 마크업이 오탐이 되지 않게 걷어낸다. */
    private static String stripComments(String html) {
        return html.replaceAll("(?s)<!--.*?-->", "");
    }

    private static Resource[] templates() throws IOException {
        return new PathMatchingResourcePatternResolver()
                .getResources("classpath*:templates/**/*.html");
    }

    private static String read(Resource resource) throws IOException {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
