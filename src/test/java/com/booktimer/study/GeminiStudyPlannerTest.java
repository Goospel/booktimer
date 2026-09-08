package com.booktimer.study;

import com.booktimer.study.GeminiStudyPlanner.PlanDay;
import com.booktimer.study.GeminiStudyPlanner.PlanInput;
import com.booktimer.study.StudyAi.AiResult;
import com.booktimer.study.StudyAi.Failure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gemini 일정 어댑터의 <b>네트워크 없는 절반</b> — 키 게이트 · 요청 조립 · 응답 파싱.
 *
 * <p>{@code GeminiReadingPersonalityNarratorTest}와 같은 규율이되 <b>재는 것이 하나 더 있다</b>:
 * Claude SDK는 구조화 출력으로 스키마를 보장했지만 여기선 우리가 직접 봉투를 열고 파싱한다 —
 * 그래서 「모델이 끝까지 못 썼다」를 우리가 알아채야 한다({@code finishReason}). 2026-09-03에 Claude가
 * 정확히 그 자리에서 잘린 일정을 냈고, 그때는 SDK의 {@code stopReason} 검사가 잡아 줬다.
 *
 * <p>{@code planUserPrompt}·{@code sanitizePlan}은 공급자와 무관한 순수 로직이라 어댑터를 옮기며 함께
 * 이사했다. 그 테스트들이 그대로 초록인 것이 <b>이사 중 로직이 새지 않았다</b>는 회귀 게이트다.
 */
class GeminiStudyPlannerTest {

    private static final ObjectMapper OM = JsonMapper.builder().build();

    private static GeminiStudyPlanner withKey(String key) {
        return new GeminiStudyPlanner(key, "gemini-2.5-flash");
    }

    /** Gemini generateContent 응답 봉투를 만든다 — {@code candidates[0].content.parts[0].text}. */
    private static String envelope(String finishReason, String innerText) {
        ObjectNode root = OM.createObjectNode();
        ArrayNode candidates = root.putArray("candidates");
        ObjectNode candidate = candidates.addObject();
        if (finishReason != null) {
            candidate.put("finishReason", finishReason);
        }
        candidate.putObject("content").putArray("parts").addObject().put("text", innerText);
        return OM.writeValueAsString(root);
    }

    private static final String ONE_DAY = "{\"days\":[{\"date\":\"2026-09-10\",\"task\":\"1장 접근통제\"}]}";

    private static PlanInput input() {
        return new PlanInput("정보보안기사", "1장 접근통제", LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 12, 3), 120, 5);
    }

    // ── 키 게이트 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("isEnabled: 키가 없거나 not-configured면 꺼진 것이다")
    void isEnabled_withoutRealKey_isFalse() {
        assertThat(withKey("not-configured").isEnabled()).isFalse();
        assertThat(withKey("").isEnabled()).isFalse();
        assertThat(withKey("   ").isEnabled()).isFalse();
        assertThat(withKey(null).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled: 진짜 키가 있으면 켜진다")
    void isEnabled_withRealKey_isTrue() {
        assertThat(withKey("AQ.real-looking-key").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("generatePlan: 키가 없으면 외부 호출 없이 DISABLED")
    void generatePlan_whenDisabled_returnsDisabled() {
        AiResult<GeminiStudyPlanner.PlanDraft> result = withKey("not-configured").generatePlan(input());

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(Failure.DISABLED);
    }

    @Test
    @DisplayName("generatePlan: 입력이 null이면 외부 호출 없이 DISABLED")
    void generatePlan_whenInputNull_returnsDisabled() {
        assertThat(withKey("AQ.real-looking-key").generatePlan(null).failure()).isEqualTo(Failure.DISABLED);
    }

    // ── 요청 조립 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("buildRequestBody: thinking을 끈다 — 안 끄면 thinking이 출력 예산을 먹고 text가 빈 채로 온다(N-060)")
    void buildRequestBody_disablesThinking() {
        JsonNode body = OM.readTree(GeminiStudyPlanner.buildRequestBody(input(), OM));

        // 상수를 참조하면 값이 무엇으로 바뀌어도 초록이다 — 0을 박아 둔다.
        assertThat(body.path("generationConfig").path("thinkingConfig").path("thinkingBudget").asInt(-1))
                .isEqualTo(0);
    }

    @Test
    @DisplayName("buildRequestBody: 출력 상한은 32,000 — 90항목 한국어 일정이 잘리지 않을 만큼")
    void buildRequestBody_capsOutputTokensAt32000() {
        JsonNode body = OM.readTree(GeminiStudyPlanner.buildRequestBody(input(), OM));

        assertThat(body.path("generationConfig").path("maxOutputTokens").asInt(-1)).isEqualTo(32_000);
    }

    @Test
    @DisplayName("buildRequestBody: JSON으로 답하라고 못 박고 온도를 낮춘다")
    void buildRequestBody_asksForDeterministicJson() {
        JsonNode config = OM.readTree(GeminiStudyPlanner.buildRequestBody(input(), OM)).path("generationConfig");

        assertThat(config.path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(config.path("temperature").asDouble(9)).isLessThanOrEqualTo(0.2);
    }

    @Test
    @DisplayName("buildRequestBody: 규칙(시스템)과 입력(사용자)이 한 프롬프트에 함께 실린다")
    void buildRequestBody_carriesRulesAndInputs() {
        String prompt = OM.readTree(GeminiStudyPlanner.buildRequestBody(input(), OM))
                .path("contents").get(0).path("parts").get(0).path("text").asText();

        assertThat(prompt).contains("정보보안기사");          // 주제
        assertThat(prompt).contains("1장 접근통제");           // 범위
        assertThat(prompt).contains("2026-09-03(목)");        // 서버가 계산한 후보 날짜
        assertThat(prompt).contains("적혀 있지 않은 단원");     // 시스템 규칙(범위 울타리)
        assertThat(prompt).contains("\"days\"");              // 출력 형식 지정
    }

    @Test
    @DisplayName("buildEndpoint: 키를 ?key= 쿼리파라미터로 싣는다(AQ 키는 헤더 방식에서 401)")
    void buildEndpoint_putsKeyInQuery() {
        String url = GeminiStudyPlanner.buildEndpoint("https://base/", "gemini-2.5-flash", "k e y");

        assertThat(url).startsWith("https://base/gemini-2.5-flash:generateContent?key=");
        assertThat(url).endsWith("k+e+y"); // URL 인코딩된다
    }

    // ── 응답 파싱 — 성공 ──────────────────────────────────────────────────

    @Test
    @DisplayName("parsePlan: 봉투를 열고 안쪽 JSON의 days를 읽는다")
    void parsePlan_readsDays() {
        AiResult<GeminiStudyPlanner.PlanDraft> result =
                GeminiStudyPlanner.parsePlan(envelope("STOP", ONE_DAY), OM);

        assertThat(result.ok()).isTrue();
        assertThat(result.value().days()).extracting(PlanDay::date).containsExactly("2026-09-10");
        assertThat(result.value().days()).extracting(PlanDay::task).containsExactly("1장 접근통제");
    }

    @Test
    @DisplayName("parsePlan: 모델이 ```json 코드펜스로 감싸도 벗겨 읽는다")
    void parsePlan_stripsCodeFence() {
        AiResult<GeminiStudyPlanner.PlanDraft> result =
                GeminiStudyPlanner.parsePlan(envelope("STOP", "```json\n" + ONE_DAY + "\n```"), OM);

        assertThat(result.ok()).isTrue();
        assertThat(result.value().days()).hasSize(1);
    }

    @Test
    @DisplayName("parsePlan: finishReason이 아예 없어도(구버전 응답) 성공으로 읽는다")
    void parsePlan_whenFinishReasonAbsent_stillOk() {
        assertThat(GeminiStudyPlanner.parsePlan(envelope(null, ONE_DAY), OM).ok()).isTrue();
    }

    // ── 응답 파싱 — 실패 ──────────────────────────────────────────────────
    //
    // 이 묶음이 이 클래스의 존재 이유다. Claude는 SDK가 stopReason으로 잘린 응답을 잡아 줬는데,
    // 여기선 우리가 안 보면 **반쪽 일정이 달력에 그대로 들어간다**(화면은 멀쩡하다).

    @Test
    @DisplayName("parsePlan: MAX_TOKENS로 잘린 응답은 성공이 아니다 — 반쪽 일정을 달력에 넣지 않는다")
    void parsePlan_whenFinishReasonIsMaxTokens_failsUnavailable() {
        AiResult<GeminiStudyPlanner.PlanDraft> result =
                GeminiStudyPlanner.parsePlan(envelope("MAX_TOKENS", ONE_DAY), OM);

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("parsePlan: SAFETY·RECITATION으로 끊긴 응답도 성공이 아니다")
    void parsePlan_whenFinishReasonIsNotStop_failsUnavailable() {
        assertThat(GeminiStudyPlanner.parsePlan(envelope("SAFETY", ONE_DAY), OM).failure())
                .isEqualTo(Failure.UNAVAILABLE);
        assertThat(GeminiStudyPlanner.parsePlan(envelope("RECITATION", ONE_DAY), OM).failure())
                .isEqualTo(Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("parsePlan: 봉투가 깨졌으면 빈 일정이 아니라 실패다")
    void parsePlan_whenEnvelopeBroken_failsUnavailable() {
        assertThat(GeminiStudyPlanner.parsePlan("{}", OM).failure()).isEqualTo(Failure.UNAVAILABLE);
        assertThat(GeminiStudyPlanner.parsePlan("{\"candidates\":[]}", OM).failure()).isEqualTo(Failure.UNAVAILABLE);
        assertThat(GeminiStudyPlanner.parsePlan("not json at all", OM).failure()).isEqualTo(Failure.UNAVAILABLE);
        assertThat(GeminiStudyPlanner.parsePlan("", OM).failure()).isEqualTo(Failure.UNAVAILABLE);
        assertThat(GeminiStudyPlanner.parsePlan(null, OM).failure()).isEqualTo(Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("parsePlan: 안쪽이 JSON이 아니거나 days가 없으면 실패다")
    void parsePlan_whenInnerShapeWrong_failsUnavailable() {
        assertThat(GeminiStudyPlanner.parsePlan(envelope("STOP", "미안하지만 못 하겠어요"), OM).failure())
                .isEqualTo(Failure.UNAVAILABLE);
        assertThat(GeminiStudyPlanner.parsePlan(envelope("STOP", "{\"items\":[]}"), OM).failure())
                .isEqualTo(Failure.UNAVAILABLE);
        assertThat(GeminiStudyPlanner.parsePlan(envelope("STOP", ""), OM).failure())
                .isEqualTo(Failure.UNAVAILABLE);
    }

    // days가 빈 배열인 것은 형식이 맞는 정상 응답이다 — 「쓸 날짜가 없다」 판정은 호출부(서비스)가
    // sanitizePlan 뒤에 한다. 여기서 실패로 만들면 그 판정이 두 곳으로 갈린다.
    @Test
    @DisplayName("parsePlan: days가 빈 배열이면 형식은 성공이다 — 비었다는 판정은 호출부 몫")
    void parsePlan_whenDaysEmpty_isOkWithEmptyList() {
        AiResult<GeminiStudyPlanner.PlanDraft> result =
                GeminiStudyPlanner.parsePlan(envelope("STOP", "{\"days\":[]}"), OM);

        assertThat(result.ok()).isTrue();
        assertThat(result.value().days()).isEmpty();
    }

    // ── planUserPrompt — 이사분(회귀 게이트) ──────────────────────────────

    @Test
    @DisplayName("planUserPrompt: 후보 날짜는 오늘부터 시험 전날까지다 — 시험 당일은 없다")
    void planUserPrompt_listsCandidateDatesUpToTheDayBeforeExam() {
        String prompt = GeminiStudyPlanner.planUserPrompt(new PlanInput(
                "정보보안기사", "1장 접근통제", LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 6), 120, 5));

        assertThat(prompt).contains("2026-09-03(목)", "2026-09-04(금)", "2026-09-05(토)");
        assertThat(prompt).doesNotContain("2026-09-06(");
    }

    @Test
    @DisplayName("planUserPrompt: 주제·범위 원문·분·주 N일이 그대로 실린다")
    void planUserPrompt_carriesTheInputs() {
        String prompt = GeminiStudyPlanner.planUserPrompt(new PlanInput(
                "정보보안기사", "1장 접근통제\n2장 암호학", LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 10), 90, 4));

        assertThat(prompt).contains("정보보안기사");
        assertThat(prompt).contains("1장 접근통제");
        assertThat(prompt).contains("2장 암호학");
        assertThat(prompt).contains("90");
        assertThat(prompt).contains("주 4일");
    }

    @Test
    @DisplayName("planUserPrompt: 범위가 비면 그 사실을 명시한다 — 빈 줄을 남기면 모델이 단원을 지어낸다")
    void planUserPrompt_whenScopeBlank_saysSo() {
        String prompt = GeminiStudyPlanner.planUserPrompt(new PlanInput(
                "정보보안기사", "   ", LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 10), 90, 4));

        assertThat(prompt).contains("범위가 주어지지 않았");
    }

    @Test
    @DisplayName("planUserPrompt: 후보 날짜가 아주 많아도 프롬프트가 폭발하지 않는다(1년치 상한)")
    void planUserPrompt_withYearLongRange_staysBounded() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        String prompt = GeminiStudyPlanner.planUserPrompt(new PlanInput(
                "주제", "범위", today, today.plusDays(365), 120, 5));

        assertThat(prompt).contains("2026-01-01", "2026-12-31");
        assertThat(prompt.length()).isLessThan(20_000);
    }

    // ── sanitizePlan — 이사분(회귀 게이트, 경계 전수) ─────────────────────

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);   // 목요일
    private static final LocalDate EXAM = LocalDate.of(2026, 10, 3);

    private static List<PlanDay> sanitize(List<PlanDay> days) {
        return GeminiStudyPlanner.sanitizePlan(days, TODAY, EXAM, 7);
    }

    private static PlanDay day(String date, String task) {
        return new PlanDay(date, task);
    }

    @Test
    @DisplayName("sanitizePlan: 오늘 이전 날짜는 버린다 — 오늘은 남는다")
    void sanitizePlan_dropsPastKeepsToday() {
        List<PlanDay> kept = sanitize(List.of(
                day("2026-09-02", "어제"), day("2026-09-03", "오늘")));

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-09-03");
    }

    @Test
    @DisplayName("sanitizePlan: 시험 당일과 그 뒤는 버린다 — 시험날엔 공부를 배정하지 않는다")
    void sanitizePlan_dropsExamDayAndLater() {
        List<PlanDay> kept = sanitize(List.of(
                day("2026-10-02", "시험 전날"), day("2026-10-03", "시험 당일"), day("2026-10-04", "시험 다음날")));

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-10-02");
    }

    @Test
    @DisplayName("sanitizePlan: 같은 날짜가 두 번 오면 앞엣것만 남는다")
    void sanitizePlan_dropsDuplicateDates() {
        List<PlanDay> kept = sanitize(List.of(
                day("2026-09-10", "먼저"), day("2026-09-10", "나중")));

        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).task()).isEqualTo("먼저");
    }

    @Test
    @DisplayName("sanitizePlan: 빈 task·공백 task는 버린다")
    void sanitizePlan_dropsBlankTasks() {
        List<PlanDay> kept = sanitize(List.of(
                day("2026-09-10", ""), day("2026-09-11", "   "), day("2026-09-12", null),
                day("2026-09-13", "1장 접근통제")));

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-09-13");
    }

    @Test
    @DisplayName("sanitizePlan: 501자 task는 500자로 자른다(버리지 않는다)")
    void sanitizePlan_truncatesLongTask() {
        List<PlanDay> kept = sanitize(List.of(day("2026-09-10", "가".repeat(501))));

        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).task()).hasSize(500);
    }

    @Test
    @DisplayName("sanitizePlan: ISO로 파싱되지 않는 날짜는 버린다 — 2026-13-45도, 빈 값도")
    void sanitizePlan_dropsUnparsableDates() {
        List<PlanDay> kept = sanitize(List.of(
                day("2026-13-45", "달이 13월"), day("9월 10일", "한글"), day("", "빈 값"),
                day(null, "널"), day("2026-09-10", "멀쩡한 날")));

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-09-10");
    }

    @Test
    @DisplayName("sanitizePlan: 주(월~일)당 daysPerWeek를 넘으면 그 주의 앞 날짜만 남는다")
    void sanitizePlan_capsPerIsoWeek() {
        List<PlanDay> kept = GeminiStudyPlanner.sanitizePlan(List.of(
                day("2026-09-07", "월"), day("2026-09-08", "화"), day("2026-09-09", "수"),
                day("2026-09-13", "일")), TODAY, EXAM, 2);

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-09-07", "2026-09-08");
    }

    @Test
    @DisplayName("sanitizePlan: 주 경계는 월요일이다 — 일요일과 그 다음 월요일은 서로 다른 주다")
    void sanitizePlan_weekBoundaryIsMonday() {
        List<PlanDay> kept = GeminiStudyPlanner.sanitizePlan(List.of(
                day("2026-09-13", "일요일"), day("2026-09-14", "다음 주 월요일")), TODAY, EXAM, 1);

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-09-13", "2026-09-14");
    }

    @Test
    @DisplayName("sanitizePlan: 날짜가 뒤섞여 와도 오름차순으로 돌려준다")
    void sanitizePlan_sortsByDate() {
        List<PlanDay> kept = sanitize(List.of(
                day("2026-09-20", "나중"), day("2026-09-10", "먼저")));

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-09-10", "2026-09-20");
    }

    @Test
    @DisplayName("sanitizePlan: 전부 걸러지면 빈 목록 — 호출부가 UNAVAILABLE로 옮긴다")
    void sanitizePlan_whenNothingSurvives_isEmpty() {
        assertThat(sanitize(List.of(day("2026-01-01", "지난해"), day("2026-11-11", "시험 뒤")))).isEmpty();
        assertThat(sanitize(List.of())).isEmpty();
        assertThat(GeminiStudyPlanner.sanitizePlan(null, TODAY, EXAM, 5)).isEmpty();
    }
}
