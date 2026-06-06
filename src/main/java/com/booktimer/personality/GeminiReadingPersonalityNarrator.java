package com.booktimer.personality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gemini Flash 기반 독서 성향 서술 어댑터(책BTI Phase 3).
 *
 * <p>{@link com.booktimer.book.AladinBookSearchClient}와 같은 패턴: API 키를 {@code @Value}로 주입받아
 * {@link #isEnabled()} 게이트로 키 없으면 폴백, HTTP는 {@link RestClient}로, JSON 매핑은 정적 메서드로
 * 분리해 네트워크 없이 단위테스트한다. 외부 호출 실패/지연은 빈 결과로 격리한다(화면 안 깨짐).
 *
 * <p>API 키는 운영에서 ECS 환경변수 {@code BOOKTIMER_LLM_API_KEY}로 주입한다(repo 미커밋). 키는 Google이
 * 문서화한 {@code ?key=} 쿼리파라미터로 싣는다 — 신형 "Authentication key"(AQ.…)는 {@code x-goog-api-key}
 * 헤더 방식에서 401({@code ACCESS_TOKEN_TYPE_UNSUPPORTED})로 거부되고, 쿼리파라미터 방식만 통하기 때문이다.
 * (URL에 키가 실리므로 catch에서 URL·요청을 로그에 남기지 않는다.)
 */
@Component
public class GeminiReadingPersonalityNarrator implements ReadingPersonalityNarrator {

    private static final Logger log = LoggerFactory.getLogger(GeminiReadingPersonalityNarrator.class);
    private static final String ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String NOT_CONFIGURED = "not-configured";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    // Boot 4 모듈러 autoconfig라 ObjectMapper 빈이 자동 등록되지 않음 → 자체 인스턴스(스레드 안전·재사용).
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiReadingPersonalityNarrator(
            @Value("${booktimer.llm.api-key:not-configured}") String apiKey,
            @Value("${booktimer.llm.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank() && !NOT_CONFIGURED.equals(apiKey);
    }

    @Override
    public Optional<PersonalityNarration> narrate(ReadingProfile profile) {
        if (!isEnabled() || profile == null) {
            return Optional.empty(); // 키 없음·입력 없음 → 외부 호출 없이 폴백
        }
        try {
            String requestBody = buildRequestBody(buildPrompt(profile, objectMapper), objectMapper);
            String response = restClient.post()
                    // 키는 ?key= 쿼리파라미터로(AQ 키는 x-goog-api-key 헤더에서 401). URI.create로 넘겨
                    // RestClient의 URI 템플릿 확장(중괄호 치환)을 우회한다.
                    .uri(URI.create(buildEndpoint(ENDPOINT_BASE, model, apiKey)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseNarration(response, objectMapper);
        } catch (Exception e) {
            // 외부 LLM 장애/지연이 화면을 깨지 않도록 빈 결과로 격리(로그만) — 호출자는 사실만 표시로 폴백.
            log.warn("Gemini 서술 생성 실패 — 사실만 표시로 폴백: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * generateContent 엔드포인트 URL을 만든다 — 키를 {@code ?key=} 쿼리파라미터로 싣는다.
     * 신형 AQ 키는 {@code x-goog-api-key} 헤더 방식에서 401로 거부되고 쿼리파라미터 방식만 통하기 때문.
     * 키에 URL 특수문자가 있어도 깨지지 않게 인코딩한다(현 키 형식엔 불필요하나 방어적).
     */
    static String buildEndpoint(String base, String model, String apiKey) {
        return base + model + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    /**
     * 그라운딩 프롬프트를 만든다 — 집계한 사실(JSON)을 주입하고 "[사실]에 있는 것만 근거, 지어내지 마라"로
     * 환각(없는 책·장르 발명)을 억제한다. 출력은 {"narrative","tags"} JSON으로만 받도록 형식을 못 박는다.
     */
    static String buildPrompt(ReadingProfile profile, ObjectMapper objectMapper) {
        String factsJson;
        try {
            factsJson = objectMapper.writeValueAsString(profile);
        } catch (Exception e) {
            factsJson = "{}";
        }
        return """
                당신은 독서 성향을 가볍게 짚어주는 'MBTI 설명문' 작가다.
                다음은 한 독자의 책장에서 집계한 사실(JSON)이다. 아래 [사실]에 있는 내용만 근거로 삼고,
                거기 없는 책·장르·정보는 지어내지 마라. 이 사람의 독서 성향을 MBTI 설명문처럼
                한 문단(3~5문장)으로 서술하고, 비교용 짧은 태그 3~5개를 함께 내라.
                반드시 다음 JSON 형식으로만 답하라: {"narrative": "<한 문단>", "tags": ["<태그>", ...]}

                [사실]
                %s
                """.formatted(factsJson);
    }

    /**
     * Gemini generateContent 요청 본문(JSON)을 만든다. 프롬프트에 따옴표·줄바꿈이 있어도 깨지지 않게
     * 문자열 조립이 아니라 Jackson 노드로 직렬화한다(이스케이프 보장). 일관성을 위해 temperature를 낮추고,
     * 응답을 JSON으로 받도록 responseMimeType을 지정한다.
     */
    static String buildRequestBody(String prompt, ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ArrayNode parts = contents.addObject().putArray("parts");
        parts.addObject().put("text", prompt);
        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.4);
        genConfig.put("responseMimeType", "application/json");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Gemini 응답(JSON)에서 설명문 + 태그를 뽑는다 — 2단 파싱: 봉투(candidates&gt;content&gt;parts&gt;text)에서
     * 모델이 낸 텍스트를 꺼내고, 그 텍스트(우리가 요청한 {"narrative","tags"} JSON)를 다시 파싱한다.
     * 비거나 형식이 어긋나거나 설명문이 비면 빈 결과(폴백).
     */
    static Optional<PersonalityNarration> parseNarration(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return Optional.empty();
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return Optional.empty();
            }
            String inner = stripFence(parts.get(0).path("text").asText(""));
            if (inner.isBlank()) {
                return Optional.empty();
            }
            JsonNode obj = objectMapper.readTree(inner);
            String narrative = obj.path("narrative").asText("").strip();
            if (narrative.isEmpty()) {
                return Optional.empty(); // 태그만으론 서술 성립 안 함 → 폴백
            }
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = obj.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode t : tagsNode) {
                    String tag = t.asText("").strip();
                    if (!tag.isEmpty()) {
                        tags.add(tag);
                    }
                }
            }
            return Optional.of(new PersonalityNarration(narrative, tags));
        } catch (Exception e) {
            log.warn("Gemini 응답 파싱 실패: {}", e.toString());
            return Optional.empty();
        }
    }

    /** 모델이 설명문 JSON을 ```json … ``` 코드펜스로 감싸는 경우가 있어 벗긴다(없으면 그대로). */
    private static String stripFence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1); // 첫 줄(``` 또는 ```json) 제거
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }
}
