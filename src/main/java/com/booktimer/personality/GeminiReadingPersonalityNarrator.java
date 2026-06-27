package com.booktimer.personality;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    // Jackson 3은 빌더로 만든다(ObjectMapper 직접 생성 폐지 — JsonMapper가 ObjectMapper를 상속).
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public GeminiReadingPersonalityNarrator(
            @Value("${booktimer.llm.api-key:not-configured}") String apiKey,
            @Value("${booktimer.llm.model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        // 타임아웃 명시 — 느린/멈춘 LLM 호출이 요청 스레드를 무한정 묶지 않게(기본 무한 대기 회피, N-060).
        // 실패를 빠르게 만들어야 호출자(reanalyze/currentPersonality)의 stale-캐시 폴백도 빠르게 동작한다.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
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
     * 그라운딩 프롬프트를 만든다 — <b>'무슨 책을 읽었는가'(장르·저자) 사실만</b> 주입하고
     * "[사실]에 있는 것만 근거, 지어내지 마라"로 환각(없는 책·장르 발명)을 억제한다.
     * 출력은 {"narrative","tags"} JSON으로만 받도록 형식을 못 박는다.
     *
     * <p>설계 의도: 책BTI는 <b>책 선택이 드러내는 사람의 내면</b>(성격·가치관·취향)만 다룬다. 독서 '습관'
     * (독서 시간·완독률·정독/다독·읽은 권수 등 행동 패턴)은 결과에서 뺀다 — 그래서 (1) 그런 신호는 아예
     * {@link #bookFactsJson 사실에서 제외}해 모델이 근거 삼을 거리를 없애고, (2) 지시로도 습관 언급을 금지한다.
     */
    static String buildPrompt(ReadingProfile profile, ObjectMapper objectMapper) {
        return """
                당신은 '읽은 책으로 사람을 읽어주는' 성향 분석가다.
                아래 [사실]은 한 독자가 *어떤 책을 읽었는가*만 추린 것이다(장르·저자 분포).
                [사실]에 있는 내용만 근거로 삼고, 거기 없는 책·장르·정보는 지어내지 마라.
                오직 '읽은 책'이 드러내는 이 사람의 성격·가치관·취향(좋아할 만한 것)을
                MBTI 설명문처럼 한 문단(3~5문장)으로 서술하고, 비교용 짧은 태그 3~5개를 함께 내라.

                [매우 중요] 독서 '습관'은 절대 언급하지 마라 — 독서 시간·완독률·정독/다독·읽은 권수 같은
                행동 패턴은 결과에 담지 말고, 오직 '무슨 책을 읽느냐'가 말해주는 내면(성격·가치관·관심사)만 다뤄라.

                반드시 다음 JSON 형식으로만 답하라: {"narrative": "<한 문단>", "tags": ["<태그>", ...]}

                [사실]
                %s
                """.formatted(bookFactsJson(profile, objectMapper));
    }

    /**
     * 프롬프트에 주입할 <b>책 내용 사실만</b> 골라 JSON으로 만든다 — 장르·저자 분포와
     * 다양성(서로 다른 저자/장르 수), 표본 크기(<b>완독 권수</b>)만 담는다. 독서 습관 신호
     * (완독률, 독서 시간·세션·평균 세션 길이)는 일부러 뺀다(설계 의도, {@link #buildPrompt}).
     * 프로필 자체가 완독 책만으로 집계되므로({@link ReadingProfileService#profileOf}) 분포·표본 모두 완독분이다.
     */
    private static String bookFactsJson(ReadingProfile profile, ObjectMapper objectMapper) {
        try {
            ObjectNode facts = objectMapper.createObjectNode();
            facts.put("finishedBooks", profile.finishedBooks()); // 표본 크기 = 완독 권수(습관 아님)
            facts.put("distinctAuthors", profile.distinctAuthors());
            facts.set("topAuthors", objectMapper.valueToTree(profile.topAuthors()));
            facts.put("distinctGenres", profile.distinctGenres());
            facts.set("topGenres", objectMapper.valueToTree(profile.topGenres()));
            return objectMapper.writeValueAsString(facts);
        } catch (Exception e) {
            return "{}";
        }
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
        // 출력 토큰 상한 — 서술 한 문단 + 태그엔 넉넉하되 무한정 늘지 않게(비용·지연 상한).
        genConfig.put("maxOutputTokens", 2048);
        // thinking 비활성(budget=0) — gemini-2.5-flash는 thinking이 기본 ON이라, maxOutputTokens 안에서
        // thinking 토큰이 예산을 소진하면 candidates[0].content.parts[0].text가 빈 문자열로 와 폴백된다(N-060).
        genConfig.putObject("thinkingConfig").put("thinkingBudget", 0);
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
