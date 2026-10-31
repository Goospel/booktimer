package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.study.StudyAiUsageService;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import com.booktimer.writing.ClaudeWritingAssistant;
import com.booktimer.writing.ClaudeWritingAssistant.Failure;
import com.booktimer.writing.ClaudeWritingAssistant.Questions;
import com.booktimer.writing.ClaudeWritingAssistant.Usage;
import com.booktimer.writing.ClaudeWritingAssistant.WritingAiResult;
import com.booktimer.writing.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/app/margins/{id}/ai}(v1 §5.1.4 · REQ-47 — 책생글 M1에서 글(draft) 대신 쪽지(story)가 대상이 됐다) — 실 H2 + Bearer 토큰 + 가짜 어댑터.
 *
 * <p>통과하면 확정: 응답에 본문·제목·revision 키가 없고(앱이 AI 응답으로 편집기를 채울 재료가 없다), 상태 코드 표의 각 갈래가 평문 안내로
 * 나간다. 순서·환불·비용 규칙 자체는 {@code WritingAiServiceTest}가 잰다 — 여기는 문(門)의 계약만.
 */
@MockedBoundaryTest
@Transactional
class WritingApiControllerAiTest {

    private static final Instant CONSENT_AT = Instant.parse("2026-11-23T00:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ClaudeWritingAssistant assistant;
    @Autowired AppleUserProvisioningService appleUsers;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BookRepository bookRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired StudyAiUsageService usageService;
    @Autowired Clock clock;

    private User user;
    private String token;
    private Story draft;

    @BeforeEach
    void setUp() {
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.model()).willReturn("claude-haiku-5-5");
        user = appleUsers.register("000777.writing.api." + SEQ.incrementAndGet());
        user.consentToAi(CONSENT_AT);
        user = userRepository.saveAndFlush(user);
        token = apiTokenService.issue(user);
        Book book = bookRepository.saveAndFlush(Book.register(user, "데미안", "헤르만 헤세", null, null, null, null, BookStatus.READING));
        draft = storyRepository.saveAndFlush(Story.of(user, "", book, "paper", "새는 알에서 나오려고 투쟁한다"));
    }

    private ResultActions ai(Long id, String json) throws Exception {
        return mvc.perform(post("/api/app/margins/" + id + "/ai").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions ask() throws Exception {
        return ai(draft.getId(), "{\"step\":\"ASK\",\"revision\":0}");
    }

    private static WritingAiResult<Questions> ok() {
        return new WritingAiResult<>(new Questions(List.of(new Question("어떤 투쟁이었나요?", "알에서 나오려고"))), null,
                new Usage(1_000, 0, 0, 300));
    }

    @Test
    @DisplayName("REQ-47 · REQ-221 · /ai 응답 JSON에 본문(text·body)·title·revision 키가 없다")
    void aiViewHasNoBody() throws Exception {
        given(assistant.ask(anyString())).willReturn(ok());

        String res = ask().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode json = JsonMapper.builder().build().readTree(res);
        List<String> keys = new ArrayList<>(json.propertyNames());
        assertThat(keys).containsExactlyInAnyOrder("questions", "aiUpdatedAt", "aiRemainingToday")
                .doesNotContain("body", "text", "quote", "title", "revision");
        assertThat(json.path("questions").path("rounds").get(0).path("questions").get(0).path("text").asString())
                .isEqualTo("어떤 투쟁이었나요?");
        assertThat(json.path("aiRemainingToday").asInt()).isEqualTo(Kind.WRITE.max() - 1);
    }

    @Test
    @DisplayName("PR-C · 쪽지 응답(MarginView)에 오늘 남은 글쓰기 AI 몫 aiRemainingToday가 있다")
    void draftViewHasRemaining() throws Exception {
        usageService.tryConsumeBoth(user, clock.instant(), Kind.WRITE);

        mvc.perform(get("/api/app/margins/" + draft.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiRemainingToday").value(Kind.WRITE.max() - 1))
                .andExpect(jsonPath("$.text").exists());
    }

    // ── 상태 코드 표 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PR-C · /ai 400 — 전제조건(FOLLOW 본문 30자 미만)은 평문 안내")
    void status400() throws Exception {
        ai(draft.getId(), "{\"step\":\"FOLLOW\",\"revision\":0}").andExpect(status().isBadRequest())
                .andExpect(content().string("내 생각을 한두 문장 쓴 뒤에 꼬리 질문을 받을 수 있어요"));
    }

    @Test
    @DisplayName("PR-C · /ai 403 — 동의 없음")
    void status403() throws Exception {
        user.withdrawAiConsent();
        userRepository.saveAndFlush(user);

        ask().andExpect(status().isForbidden()).andExpect(content().string("AI 질문을 받으려면 안내를 읽고 동의해 주세요"));
    }

    @Test
    @DisplayName("PR-C · /ai 404 — 남의·없는 글")
    void status404() throws Exception {
        User other = appleUsers.register("000777.writing.api.other." + SEQ.incrementAndGet());
        other.consentToAi(CONSENT_AT);
        userRepository.saveAndFlush(other);
        String otherToken = apiTokenService.issue(other);

        mvc.perform(post("/api/app/margins/" + draft.getId() + "/ai").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"step\":\"ASK\",\"revision\":0}"))
                .andExpect(status().isNotFound());
        ai(Long.MAX_VALUE, "{\"step\":\"ASK\",\"revision\":0}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PR-C · /ai 409 — revision 다름")
    void status409() throws Exception {
        ai(draft.getId(), "{\"step\":\"ASK\",\"revision\":3}").andExpect(status().isConflict())
                .andExpect(content().string("글이 아직 저장되지 않았어요 — 저장 뒤 다시 눌러 주세요"));
    }

    @Test
    @DisplayName("PR-C · /ai 422 — 거절")
    void status422() throws Exception {
        given(assistant.ask(anyString())).willReturn(new WritingAiResult<>(null, Failure.REFUSED, new Usage(100, 0, 0, 10)));

        ask().andExpect(status().isUnprocessableContent())
                .andExpect(content().string("이 내용으로는 AI가 질문을 만들기 어려워요 — 글은 그대로 저장돼 있어요"));
    }

    @Test
    @DisplayName("PR-C · /ai 429 — 내 하루 몫")
    void status429() throws Exception {
        for (int i = 0; i < Kind.WRITE.max(); i++) {
            usageService.tryConsumeBoth(user, clock.instant(), Kind.WRITE);
        }

        ask().andExpect(status().isTooManyRequests())
                .andExpect(content().string("오늘 받을 수 있는 AI 질문을 다 받았어요 — 한국 시간 오전 9시에 다시 채워져요"));
    }

    @Test
    @DisplayName("PR-C · /ai 503 — 키 없음 · 응답 없음")
    void status503() throws Exception {
        given(assistant.ask(anyString())).willReturn(new WritingAiResult<>(null, Failure.NO_RESPONSE, null));
        ask().andExpect(status().isServiceUnavailable())
                .andExpect(content().string("이번엔 질문을 만들지 못했어요 — 잠시 뒤 다시 눌러 주세요(이번 몫은 돌려 드렸어요)"));

        given(assistant.isEnabled()).willReturn(false);
        ask().andExpect(status().isServiceUnavailable())
                .andExpect(content().string("지금은 AI 질문이 꺼져 있어요 — 글은 그대로 쓸 수 있어요"));
    }
}
