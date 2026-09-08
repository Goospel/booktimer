package com.booktimer.study;

import com.booktimer.study.StudyAi.AiResult;
import com.booktimer.study.StudyAi.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gemini Flash 기반 공부 일정 생성 어댑터 — 2026-09-08에 Claude Sonnet에서 옮겨 왔다.
 *
 * <p><b>왜 옮겼나</b>: 일정은 출력이 나머지 둘(분석 8,192 · 전사 4,096)의 4~8배라 세 능력 중 단가가
 * 가장 높았다. 방어선({@link #sanitizePlan})이 애초에 공급자와 무관하게 「모델을 믿지 않는다」로 짜여
 * 있어서, 공급자를 갈아도 지켜야 할 규칙은 그대로 선다.
 *
 * <p>{@link com.booktimer.personality.GeminiReadingPersonalityNarrator}와 같은 규율이다: 키를
 * {@code @Value}로 받아 {@link #isEnabled()} 게이트로 없으면 아예 나가지 않고, 타임아웃을 명시하고,
 * 프롬프트 조립·응답 파싱은 <b>정적 메서드</b>로 떼어 네트워크 없이 잰다.
 *
 * <p><b>Claude와 결정적으로 다른 한 가지</b>: 거기선 SDK 구조화 출력이 스키마를 보장하고
 * {@code stopReason}으로 잘린 응답까지 걸러 줬다. 여기선 봉투를 우리가 연다 — 그래서
 * {@link #parsePlan}이 {@code finishReason}을 직접 본다. 안 보면 <b>반쪽 일정이 달력에 그대로 들어간다</b>
 * (2026-09-03에 Claude가 정확히 그 자리에서 잘렸고, 그때는 SDK가 잡아 줬다).
 *
 * <p>승인 게이트는 여기 없다 — {@code StudyAiAccessService.requireApproved}가 호출부 첫 줄에서 정한다.
 */
@Component
public class GeminiStudyPlanner {

    private static final Logger log = LoggerFactory.getLogger(GeminiStudyPlanner.class);
    private static final String ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String NOT_CONFIGURED = "not-configured";

    /**
     * 출력 토큰 상한.
     *
     * <p>Claude 시절 설계값은 8,192였는데, 실측에서 그 조합이 {@code stopReason=max_tokens}로 잘려
     * 503이 났다(2026-09-03 로컬 실키 — 정보보안기사 5단원 · 시험일 3개월 뒤 · 주 6일). 3개월·주 6일이면
     * 항목이 80개에 가깝고 한국어 task는 글자당 토큰이 비싸다. 출력 토큰은 <b>쓴 만큼</b> 과금되므로
     * 한도를 올려도 평소 비용은 그대로다. Flash의 상한(65,535) 안이다.
     */
    private static final int MAX_OUTPUT_TOKENS = 32_000;

    /**
     * 일정 시스템 프롬프트 — <b>범위 밖으로 나가지 마라</b>가 요점이다.
     *
     * <p>모델은 「정보보안기사」라는 주제명만 보고도 그럴듯한 커리큘럼을 지어낼 수 있다. 그 일정은
     * 사용자가 실제로 가진 책·범위와 어긋나고, 어긋난 일정은 매일 달력에 떠서 사람을 헷갈리게 한다.
     * 그래서 「적힌 단원만」이고, 날짜도 우리가 계산해 준 후보 안에서만 고르게 한다.
     *
     * <p>마지막 줄의 출력 형식 지정이 Claude 시절엔 없었다 — SDK가 record에서 스키마를 만들어 줬기
     * 때문이다. Gemini엔 그 장치가 없으므로 형식을 말로 못 박고 {@link #parsePlan}이 검사한다.
     */
    private static final String PLAN_SYSTEM = """
            당신은 수험 일정을 짜 주는 보조다. 주제, 공부할 범위, 시험일, 하루 공부 시간, 주 공부일수,
            그리고 배정 가능한 후보 날짜 목록이 주어진다.

            반드시 지킬 것:
            - **범위 텍스트에 적힌 단원·항목만** 배분한다. 적혀 있지 않은 단원을 만들어 내지 않는다.
            - 날짜는 **주어진 후보 날짜 중에서만** 고른다. 후보에 없는 날짜를 쓰지 않는다.
            - 한 주(월요일~일요일)에 **주 공부일수만큼만** 고른다. 그보다 많이 넣지 않는다.
            - 하루 분량은 하루 공부 시간(분)에 맞춘다. 시간이 짧으면 쪼개고, 길면 묶는다.
            - 시험 전날은 총복습으로 둔다. 마지막 3일 중 하루는 취약한 부분 재점검으로 둔다.
            - task는 한국어 한 줄(120자 이내)로 쓰고, 범위에 적힌 표기(장 번호·쪽수)를 그대로 옮긴다.
            - date는 YYYY-MM-DD 형식으로 쓴다.

            설명도 인사말도 코드펜스도 없이, 반드시 다음 JSON 형식으로만 답한다:
            {"days": [{"date": "YYYY-MM-DD", "task": "<한 줄>"}]}
            """;

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    // Boot 4 모듈러 autoconfig라 ObjectMapper 빈이 자동 등록되지 않음 → 자체 인스턴스(스레드 안전·재사용).
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public GeminiStudyPlanner(@Value("${booktimer.llm.api-key:not-configured}") String apiKey,
                              @Value("${booktimer.llm.plan-model:gemini-2.5-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        // RestClient를 성향 서술(GeminiReadingPersonalityNarrator)과 <b>공유하지 않는다</b> — 거기 읽기
        // 타임아웃 20초는 stale 캐시 폴백을 빠르게 하려는 의도적 설계인데, 90항목짜리 일정은 그 안에
        // 안 끝난다. 90초는 Claude 클라이언트가 쓰던 값과 같다.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank() && !NOT_CONFIGURED.equals(apiKey);
    }

    /**
     * 시험일까지의 날짜별 일정을 만든다.
     *
     * <p>정제({@link #sanitizePlan})는 여기서 하지 않는다 — 어댑터를 목으로 갈아끼우는 테스트에서
     * 정제까지 함께 사라지면 그 규칙엔 계측기가 없어진다. 호출부(서비스)가 부른다.
     *
     * @return 성공이면 모델이 낸 <b>날것</b>(범위 밖 날짜·중복이 섞여 있을 수 있다), 실패면 {@link Failure}
     */
    public AiResult<PlanDraft> generatePlan(PlanInput in) {
        if (!isEnabled() || in == null) {
            return AiResult.fail(Failure.DISABLED);
        }
        long started = System.currentTimeMillis();
        try {
            String response = restClient.post()
                    // 키는 ?key= 쿼리파라미터로(AQ 키는 x-goog-api-key 헤더에서 401). URI.create로 넘겨
                    // RestClient의 URI 템플릿 확장(중괄호 치환)을 우회한다.
                    .uri(URI.create(buildEndpoint(ENDPOINT_BASE, model, apiKey)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(in, objectMapper))
                    .retrieve()
                    .body(String.class);
            log.info("gemini plan {}ms", System.currentTimeMillis() - started);
            return parsePlan(response, objectMapper);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Gemini 일정 레이트리밋 — status={}", e.getStatusCode());
            return AiResult.fail(Failure.RATE_LIMITED);
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Gemini 일정 요청 거부 — status={}", e.getStatusCode());
            return AiResult.fail(Failure.BAD_INPUT);
        } catch (Exception e) {
            // ⚠️ e.toString()·e.getMessage()를 찍지 않는다 — 키가 URL 쿼리에 실려 있어, URI를 message에
            // 담는 예외(ResourceAccessException 등)가 시크릿을 로그로 흘린다. 클래스명만 남긴다.
            log.warn("Gemini 일정 실패 — {}", e.getClass().getSimpleName());
            return AiResult.fail(Failure.UNAVAILABLE);
        }
    }

    // ── 순수(정적) — 네트워크 없이 단위테스트하는 절반 ──

    /**
     * generateContent 엔드포인트 URL을 만든다 — 키를 {@code ?key=} 쿼리파라미터로 싣는다.
     * 신형 AQ 키는 {@code x-goog-api-key} 헤더 방식에서 401로 거부되고 쿼리파라미터 방식만 통한다.
     */
    static String buildEndpoint(String base, String model, String apiKey) {
        return base + model + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    /**
     * Gemini generateContent 요청 본문(JSON)을 만든다.
     *
     * <p>규칙(시스템)과 입력(사용자)을 <b>한 프롬프트로 합친다</b> — {@code systemInstruction} 필드를
     * 쓰는 편이 정석이지만 이 레포에서 실측된 적이 없고, 성향 서술이 석 달째 합치는 방식으로 돌고 있다.
     * 프롬프트에 따옴표·줄바꿈이 있어도 깨지지 않게 문자열 조립이 아니라 Jackson 노드로 직렬화한다.
     */
    static String buildRequestBody(PlanInput in, ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ArrayNode parts = contents.addObject().putArray("parts");
        parts.addObject().put("text", PLAN_SYSTEM + "\n" + planUserPrompt(in));

        ObjectNode genConfig = root.putObject("generationConfig");
        // 일정은 성향 서술(0.4)보다 결정적이어야 한다 — 같은 범위에 매번 다른 배분이 나오면 「다시
        // 만들기」가 도박이 된다.
        genConfig.put("temperature", 0.2);
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        // thinking 비활성(budget=0) — gemini-2.5-flash는 thinking이 기본 ON이라, maxOutputTokens 안에서
        // thinking 토큰이 예산을 소진하면 parts[0].text가 빈 문자열로 온다(N-060, 성향 서술의 실측).
        genConfig.putObject("thinkingConfig").put("thinkingBudget", 0);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Gemini 응답(JSON)에서 일정 초안을 뽑는다 — <b>2단 파싱</b>: 봉투
     * ({@code candidates>content>parts>text})에서 모델이 낸 텍스트를 꺼내고, 그 텍스트(우리가 요청한
     * {@code {"days":[…]}} JSON)를 다시 파싱한다.
     *
     * <p><b>{@code finishReason} 검사가 이 메서드의 핵심이다.</b> Claude에선 SDK의 {@code stopReason}이
     * 이 몫을 했다 — 끝까지 못 쓴 응답을 성공으로 치면 <b>반쪽 일정이 그대로 달력에 들어가고</b>,
     * 사용자 눈엔 「AI가 대충 짰네」로 보일 뿐 장애로 보이지 않는다. {@code STOP}이 아닌 값
     * ({@code MAX_TOKENS} · {@code SAFETY} · {@code RECITATION} · {@code OTHER})은 전부 실패다.
     * 값이 <b>아예 없는</b> 경우만 통과시킨다(응답 형태가 달라졌을 때 멀쩡한 일정까지 버리지 않으려고).
     *
     * <p>{@code days}가 <b>빈 배열</b>인 것은 형식이 맞는 정상 응답이라 성공이다 — 「쓸 날짜가 하나도
     * 없다」는 판정은 {@link #sanitizePlan} 뒤에 호출부가 한다(판정이 두 곳으로 갈리지 않게).
     */
    static AiResult<PlanDraft> parsePlan(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return AiResult.fail(Failure.UNAVAILABLE);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return AiResult.fail(Failure.UNAVAILABLE);
            }
            JsonNode candidate = candidates.get(0);

            String finishReason = candidate.path("finishReason").asText("");
            if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
                log.warn("Gemini 일정 중단 — finishReason={}", finishReason);
                return AiResult.fail(Failure.UNAVAILABLE);
            }

            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return AiResult.fail(Failure.UNAVAILABLE);
            }
            String inner = stripFence(parts.get(0).path("text").asText(""));
            if (inner.isBlank()) {
                return AiResult.fail(Failure.UNAVAILABLE);
            }

            JsonNode days = objectMapper.readTree(inner).path("days");
            if (!days.isArray()) {
                return AiResult.fail(Failure.UNAVAILABLE);
            }
            List<PlanDay> parsed = new ArrayList<>();
            for (JsonNode day : days) {
                // 빈 문자열로 받아 둔다 — 못 읽는 날짜·빈 task를 버리는 것은 sanitizePlan의 일이다.
                parsed.add(new PlanDay(day.path("date").asText(""), day.path("task").asText("")));
            }
            return AiResult.ok(new PlanDraft(List.copyOf(parsed)));
        } catch (Exception e) {
            log.warn("Gemini 일정 응답 파싱 실패 — {}", e.getClass().getSimpleName());
            return AiResult.fail(Failure.UNAVAILABLE);
        }
    }

    /** 모델이 JSON을 ```json … ``` 코드펜스로 감싸는 경우가 있어 벗긴다(없으면 그대로). */
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

    /**
     * 일정 요청의 user 메시지 — <b>후보 날짜를 서버가 계산해</b> 넣는다.
     *
     * <p>「오늘부터 시험 전날까지」를 모델에게 세게 하지 않는 이유는 두 가지다. ① 날짜 산술은 모델이
     * 조용히 틀리는 대표적인 자리이고(윤년·월말), ② 후보를 우리가 주면 「시험 당일엔 배정하지 않는다」는
     * 규칙이 프롬프트의 지시가 아니라 <b>입력의 형태</b>가 된다 — 지시는 무시될 수 있어도 없는 날짜는
     * 고를 수 없다({@link #sanitizePlan}이 그래도 한 번 더 거른다).
     *
     * <p>요일을 붙이는 것은 「주 N일」을 고르는 데 필요해서다(모델이 날짜에서 요일을 다시 세지 않게).
     */
    static String planUserPrompt(PlanInput in) {
        String scope = blankToNull(in.scope());
        StringBuilder candidates = new StringBuilder();
        for (LocalDate date = in.today(); date.isBefore(in.examDate()); date = date.plusDays(1)) {
            if (!candidates.isEmpty()) {
                candidates.append(' ');
            }
            candidates.append(date).append('(').append(WEEKDAYS[date.getDayOfWeek().getValue() - 1]).append(')');
        }
        // ⚠️ 주제·범위는 사용자가 친 글이라 신뢰할 수 없다 — 라벨을 흉내 내 섹션을 위조할 수 있다.
        // 폭발 반경이 자기 일정뿐이라(툴·외부 호출로 새는 경로가 없다) 지금은 막지 않는다.
        // 여기에 툴 사용이 붙는 날에는 구분자·이스케이프를 먼저 넣어야 한다.
        return """
                [주제] %s
                [범위]
                %s
                [시험일] %s
                [하루 공부 시간] %d분
                [주 공부일수] 주 %d일
                [배정 가능한 후보 날짜] %s
                """.formatted(
                in.subject() == null ? "(적지 않음)" : in.subject().strip(),
                scope == null ? "범위가 주어지지 않았어요 — 주제만 보고 단원을 지어내지 말고, 큰 흐름의 복습 일정으로 짜 주세요" : scope,
                in.examDate(), in.dailyMinutes(), in.daysPerWeek(), candidates);
    }

    private static final String[] WEEKDAYS = {"월", "화", "수", "목", "금", "토", "일"};

    /**
     * 모델이 낸 일정 초안을 <b>믿지 않고</b> 다듬는다 — 이 메서드가 이 판의 방어선이다.
     *
     * <p>공급자와 무관한 순수 로직이라 Claude에서 여기로 <b>그대로</b> 이사했다(테스트도 함께). 모델을
     * 갈아도 지켜야 할 규칙은 같기 때문이다.
     *
     * <p>거르는 것: 파싱 안 되는 날짜 · 오늘 이전 · 시험 당일 이후(시험날엔 배정하지 않는다) · 같은 날짜
     * 중복(앞엣것이 이긴다) · 빈 할 일. 자르는 것: {@value StudyPlanItem#TASK_MAX}자를 넘는 할 일.
     * 그리고 <b>주(월요일~일요일)당 {@code daysPerWeek}개</b>까지만 남긴다.
     *
     * <p>주 경계는 <b>ISO-8601</b>이다 — 월요일이 첫날이고 일요일이 마지막이다. 일요일과 그 다음 월요일은
     * 서로 다른 주라, 일요일에 하나 월요일에 하나면 「주 1일」을 두 번 지킨 것이다. 결과를 날짜 오름차순으로
     * 돌려주므로 주당 상한에 남는 것은 <b>그 주의 앞 날짜들</b>이다.
     *
     * @return 오름차순 정제 결과. 전부 걸러지면 빈 목록 — 호출부가 {@link Failure#UNAVAILABLE}로 옮긴다
     */
    static List<PlanDay> sanitizePlan(List<PlanDay> days, LocalDate today, LocalDate examDate, int daysPerWeek) {
        if (days == null || days.isEmpty()) {
            return List.of();
        }
        // TreeMap 하나가 정렬과 중복 제거를 같이 한다(putIfAbsent라 목록에서 먼저 온 쪽이 이긴다).
        Map<LocalDate, String> byDate = new TreeMap<>();
        for (PlanDay day : days) {
            LocalDate date = parseIsoOrNull(day == null ? null : day.date());
            if (date == null || date.isBefore(today) || !date.isBefore(examDate)) {
                continue;
            }
            String task = day.task() == null ? "" : day.task().strip();
            if (task.isEmpty()) {
                continue;
            }
            byDate.putIfAbsent(date, task.length() > StudyPlanItem.TASK_MAX
                    ? task.substring(0, StudyPlanItem.TASK_MAX) : task);
        }
        Map<LocalDate, Integer> perWeek = new HashMap<>();
        List<PlanDay> kept = new ArrayList<>();
        for (Map.Entry<LocalDate, String> entry : byDate.entrySet()) {
            // ISO 주의 시작(월요일)을 열쇠로 센다 — DayOfWeek 조정자가 곧 ISO 주 경계다.
            LocalDate weekStart = entry.getKey().with(DayOfWeek.MONDAY);
            if (perWeek.merge(weekStart, 1, Integer::sum) > daysPerWeek) {
                continue;
            }
            kept.add(new PlanDay(entry.getKey().toString(), entry.getValue()));
        }
        return List.copyOf(kept);
    }

    private static LocalDate parseIsoOrNull(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    // ── 형(型) ──

    /**
     * 일정 생성 입력 — 검증(시험일이 미래인가, 분·일수가 범위 안인가)은 <b>호출부가 이미 끝냈다</b>.
     *
     * @param today        유저 타임존 기준 오늘 — 후보 날짜의 첫날
     * @param examDate     시험일. 이 날은 후보에서 <b>빠진다</b>(시험날에 공부를 배정하지 않는다)
     * @param dailyMinutes 하루 공부 시간(분)
     * @param daysPerWeek  주 공부일수(1~7)
     */
    public record PlanInput(String subject, String scope, LocalDate today, LocalDate examDate,
                            int dailyMinutes, int daysPerWeek) {
    }

    /**
     * 하루치 일정 — 날짜가 <b>문자열</b>이다({@code YYYY-MM-DD}).
     *
     * <p>모델이 「2026-13-45」를 보내는 것도 정상 동작이므로, {@code LocalDate}로 받을 수 없다 —
     * 파싱은 {@link #sanitizePlan}의 일이고, 못 읽는 날짜는 거기서 버려진다.
     */
    public record PlanDay(String date, String task) {
    }

    /** 모델이 낸 일정 초안 한 판 — 정제 전의 날것이다. */
    public record PlanDraft(List<PlanDay> days) {
    }
}
