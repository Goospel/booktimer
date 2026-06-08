package com.booktimer.personality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gemini 어댑터 단위 테스트 — 네트워크 없이 응답 파싱·프롬프트 그라운딩·요청 본문·활성 게이트만 본다(책BTI Phase 3).
 *
 * <p>실제 HTTP 호출은 단위테스트하지 않는다(알라딘 어댑터와 동일 정신 — parse/build/guards만 정적으로 검증).
 */
class GeminiReadingPersonalityNarratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_RESPONSE = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "{\\"narrative\\": \\"이 독자는 소설에 깊이 빠지는 정독형이다.\\", \\"tags\\": [\\"정독형\\", \\"소설러\\"]}" }
                    ]
                  }
                }
              ]
            }
            """;

    @Test
    @DisplayName("응답 파싱: Gemini 봉투(candidates>content>parts>text) 안의 JSON에서 설명문·태그를 뽑는다")
    void parseNarration_extractsNarrativeAndTags() {
        Optional<PersonalityNarration> result =
                GeminiReadingPersonalityNarrator.parseNarration(GEMINI_RESPONSE, objectMapper);

        assertThat(result).isPresent();
        assertThat(result.get().narrative()).isEqualTo("이 독자는 소설에 깊이 빠지는 정독형이다.");
        assertThat(result.get().tags()).containsExactly("정독형", "소설러");
    }

    @Test
    @DisplayName("응답 파싱: 모델이 ```json 펜스로 감싸도 벗겨서 파싱한다(견고성)")
    void parseNarration_stripsMarkdownFence() {
        String fenced = """
                {
                  "candidates": [
                    { "content": { "parts": [
                      { "text": "```json\\n{\\"narrative\\": \\"잡식형 독자.\\", \\"tags\\": [\\"잡식\\"]}\\n```" }
                    ] } }
                  ]
                }
                """;

        Optional<PersonalityNarration> result =
                GeminiReadingPersonalityNarrator.parseNarration(fenced, objectMapper);

        assertThat(result).isPresent();
        assertThat(result.get().narrative()).isEqualTo("잡식형 독자.");
        assertThat(result.get().tags()).containsExactly("잡식");
    }

    @Test
    @DisplayName("응답 파싱: 빈/오류/설명문 없는 응답은 빈 결과(폴백)")
    void parseNarration_handlesBadOrEmpty() {
        assertThat(GeminiReadingPersonalityNarrator.parseNarration(null, objectMapper)).isEmpty();
        assertThat(GeminiReadingPersonalityNarrator.parseNarration("", objectMapper)).isEmpty();
        assertThat(GeminiReadingPersonalityNarrator.parseNarration("not json", objectMapper)).isEmpty();
        // 봉투는 맞지만 candidates가 비면 폴백
        assertThat(GeminiReadingPersonalityNarrator.parseNarration("{\"candidates\":[]}", objectMapper)).isEmpty();
        // 내부 JSON에 narrative가 비면 폴백(태그만으론 서술 성립 안 함)
        String noNarrative = """
                {"candidates":[{"content":{"parts":[{"text":"{\\"narrative\\":\\"  \\",\\"tags\\":[\\"x\\"]}"}]}}]}
                """;
        assertThat(GeminiReadingPersonalityNarrator.parseNarration(noNarrative, objectMapper)).isEmpty();
    }

    // ---- 엔드포인트(인증 방식) ----

    @Test
    @DisplayName("엔드포인트: 키를 x-goog-api-key 헤더가 아니라 ?key= 쿼리파라미터로 싣는다(AQ 키 호환)")
    void buildEndpoint_putsKeyAsQueryParam() {
        String base = "https://generativelanguage.googleapis.com/v1beta/models/";

        String endpoint = GeminiReadingPersonalityNarrator.buildEndpoint(base, "gemini-2.5-flash", "AQ.test-key");

        // 모델별 generateContent 경로 + 키는 쿼리파라미터로(헤더 방식은 AQ 키에서 401이라 회피)
        assertThat(endpoint)
                .startsWith(base + "gemini-2.5-flash:generateContent")
                .contains("key=AQ.test-key");
    }

    // ---- 활성 게이트 ----

    @Test
    @DisplayName("API 키 미설정이면 비활성(사실만 표시로 폴백), 설정되면 활성")
    void isEnabled_dependsOnApiKey() {
        assertThat(new GeminiReadingPersonalityNarrator("not-configured", "gemini-2.0-flash").isEnabled()).isFalse();
        assertThat(new GeminiReadingPersonalityNarrator("  ", "gemini-2.0-flash").isEnabled()).isFalse();
        assertThat(new GeminiReadingPersonalityNarrator("real-key-123", "gemini-2.0-flash").isEnabled()).isTrue();
    }

    // ---- 프롬프트 그라운딩 ----

    @Test
    @DisplayName("프롬프트: '주어진 사실만/지어내지 마라' 그라운딩 + 출력 JSON 형식 + 프로필 사실(장르)을 담는다")
    void buildPrompt_groundsAndCarriesFacts() {
        ReadingProfile profile = new ReadingProfile(5, 3, 1, 1, 0.6, 7200, 4, 1800,
                1, List.of(new LabeledCount("김영하", 2)),
                1, List.of(new LabeledCount("소설/시/희곡", 3)),
                List.of(new LabeledCount("2020", 4)));

        String prompt = GeminiReadingPersonalityNarrator.buildPrompt(profile, objectMapper);

        assertThat(prompt).contains("지어내지"); // 환각 억제 그라운딩
        assertThat(prompt).contains("narrative").contains("tags"); // 출력 형식 지시
        assertThat(prompt).contains("소설/시/희곡"); // 집계된 사실이 프롬프트에 실림
    }

    // ---- 요청 본문(이스케이프) ----

    @Test
    @DisplayName("요청 본문: 따옴표·줄바꿈이 든 프롬프트도 유효한 JSON으로 싸고, 그 텍스트를 그대로 담는다")
    void buildRequestBody_escapesAndEmbedsPrompt() throws Exception {
        String prompt = "줄1 \"따옴표\"\n줄2"; // 이스케이프 깨지기 쉬운 입력

        String body = GeminiReadingPersonalityNarrator.buildRequestBody(prompt, objectMapper);

        JsonNode root = objectMapper.readTree(body); // 유효 JSON이어야(파싱 실패 시 Red)
        assertThat(root.path("contents").get(0).path("parts").get(0).path("text").asText())
                .isEqualTo(prompt);
        assertThat(root.path("generationConfig").path("responseMimeType").asText())
                .isEqualTo("application/json");
    }

    @Test
    @DisplayName("요청 본문: 출력 토큰 상한 + thinking 비활성으로 빈 응답을 방어한다(N-060: 2.5-flash thinking이 예산 소진하면 text 빔)")
    void buildRequestBody_boundsOutputAndDisablesThinking() throws Exception {
        String body = GeminiReadingPersonalityNarrator.buildRequestBody("아무 프롬프트", objectMapper);

        JsonNode genConfig = objectMapper.readTree(body).path("generationConfig");
        // maxOutputTokens가 양수로 설정돼 응답이 한없이 늘지 않게 한다(서술 한 문단엔 충분)
        assertThat(genConfig.path("maxOutputTokens").asInt(0)).isGreaterThan(0);
        // thinkingBudget=0 → gemini-2.5-flash의 thinking을 꺼서 thinking 토큰이 출력 예산을 삼켜
        // parts[0].text가 빈 문자열로 오는 것을 막는다
        assertThat(genConfig.path("thinkingConfig").path("thinkingBudget").asInt(-1)).isEqualTo(0);
    }

    // ---- narrate 가드(네트워크 없이) ----

    @Test
    @DisplayName("narrate 가드: 비활성(키 없음)이거나 프로필이 null이면 외부 호출 없이 빈 결과")
    void narrate_guards_noNetwork() {
        ReadingProfile anyProfile = new ReadingProfile(1, 0, 0, 1, 0.0, 0, 0, 0,
                0, List.of(), 0, List.of(), List.of());

        // 키 없음 → 폴백
        assertThat(new GeminiReadingPersonalityNarrator("not-configured", "gemini-2.0-flash")
                .narrate(anyProfile)).isEmpty();
        // 키 있어도 프로필 null → 호출 전에 폴백
        assertThat(new GeminiReadingPersonalityNarrator("real-key-123", "gemini-2.0-flash")
                .narrate(null)).isEmpty();
    }
}
