package com.booktimer.study;

import com.booktimer.study.ClaudeStudyAssistant.PlanDay;
import com.booktimer.study.ClaudeStudyAssistant.PlanInput;
import com.booktimer.study.ClaudeStudyAssistant.RecallAnalysis;
import com.booktimer.study.ClaudeStudyAssistant.RecallInput;
import com.booktimer.study.ClaudeStudyAssistant.Transcript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Claude 어댑터의 <b>네트워크 없는 절반</b> — 키 게이트 · 프롬프트 조립 · 응답 정제.
 *
 * <p>{@code GeminiReadingPersonalityNarrator}와 같은 규율이다: 외부에 나가는 부분은 목으로 대체하고
 * (컨트롤러 테스트가 그 몫), 우리가 실제로 쓴 로직만 여기서 잰다. 정제(normalize)를 재는 이유는 모델이
 * 「빈 문자열 항목」·「스무 개짜리 배열」을 보내는 것이 정상 동작이기 때문이다 — 그게 그대로 DB에 들어가면
 * 화면이 빈 줄로 도배된다.
 */
class ClaudeStudyAssistantTest {

    private static ClaudeStudyAssistant withKey(String key) {
        return new ClaudeStudyAssistant(key, "claude-sonnet-5");
    }

    @Test
    @DisplayName("isEnabled: 빈 값·공백·not-configured는 꺼짐 — 클라이언트를 만들지 않는다")
    void isEnabled_withoutRealKey_isFalse() {
        assertThat(withKey("").isEnabled()).isFalse();
        assertThat(withKey("   ").isEnabled()).isFalse();
        assertThat(withKey("not-configured").isEnabled()).isFalse();
        assertThat(withKey(null).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled: 실값이면 켜짐")
    void isEnabled_withRealKey_isTrue() {
        assertThat(withKey("sk-ant-api03-어떤값").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("model(): 주입한 모델 이름을 그대로 돌려준다 — 저장되는 model 컬럼의 출처")
    void model_isTheInjectedOne() {
        assertThat(withKey("k").model()).isEqualTo("claude-sonnet-5");
    }

    // ── 프롬프트 ──

    @Test
    @DisplayName("recallUserPrompt: 과목·범위·본문이 전부 실린다")
    void recallUserPrompt_carriesSubjectScopeAndBody() {
        String prompt = ClaudeStudyAssistant.recallUserPrompt(
                new RecallInput("정보처리기사 실기", "3장 함수 p.45-70", "함수는 입력을 받아 출력을 낸다"));

        assertThat(prompt).contains("정보처리기사 실기")
                .contains("3장 함수 p.45-70")
                .contains("함수는 입력을 받아 출력을 낸다");
    }

    @Test
    @DisplayName("recallUserPrompt: 범위가 비면 「범위 없음」을 명시한다 — 빈 울타리를 모델이 넓게 해석하지 않게")
    void recallUserPrompt_withoutScope_saysSo() {
        String withScope = ClaudeStudyAssistant.recallUserPrompt(
                new RecallInput("과목", "3장", "본문"));
        String withoutScope = ClaudeStudyAssistant.recallUserPrompt(
                new RecallInput("과목", "   ", "본문"));

        assertThat(withoutScope).isNotEqualTo(withScope);
        assertThat(withoutScope).contains("범위 없음");
    }

    @Test
    @DisplayName("recallUserPrompt: 과목이 비어도 깨지지 않는다(자유 작성) — null도 마찬가지")
    void recallUserPrompt_withoutSubject_stillBuilds() {
        String prompt = ClaudeStudyAssistant.recallUserPrompt(new RecallInput(null, null, "본문만 있다"));

        assertThat(prompt).contains("본문만 있다");
    }

    // ── 정제 ──

    @Test
    @DisplayName("normalize: 요약이 비면 빈 결과 — 정리 없는 분석은 저장할 값이 없다")
    void normalize_blankSummary_isEmpty() {
        assertThat(ClaudeStudyAssistant.normalize(
                new RecallAnalysis("   ", List.of("구멍"), List.of("문제")))).isEmpty();
        assertThat(ClaudeStudyAssistant.normalize(
                new RecallAnalysis(null, List.of("구멍"), List.of("문제")))).isEmpty();
        assertThat(ClaudeStudyAssistant.normalize((RecallAnalysis) null)).isEmpty();
    }

    @Test
    @DisplayName("normalize: 앞뒤 공백을 털고 빈 항목·null 항목을 버린다")
    void normalize_trimsAndDropsBlanks() {
        List<String> holes = new ArrayList<>(Arrays.asList("  구멍 하나  ", "", "   ", null, "구멍 둘"));

        Optional<RecallAnalysis> result = ClaudeStudyAssistant.normalize(
                new RecallAnalysis("  정리한 내용  ", holes, null));

        assertThat(result).isPresent();
        assertThat(result.get().summary()).isEqualTo("정리한 내용");
        assertThat(result.get().holes()).containsExactly("구멍 하나", "구멍 둘");
        assertThat(result.get().questions()).isEmpty(); // null 배열 → 빈 배열(화면이 그대로 그린다)
    }

    @Test
    @DisplayName("normalize: 구멍·문제는 각각 10개까지만 남긴다 — 스무 개짜리 배열이 화면을 덮지 않게")
    void normalize_capsListsAtTen() {
        List<String> many = IntStream.rangeClosed(1, 25).mapToObj(i -> "항목 " + i).toList();

        RecallAnalysis result = ClaudeStudyAssistant.normalize(
                new RecallAnalysis("정리", many, many)).orElseThrow();

        assertThat(result.holes()).hasSize(10).first().isEqualTo("항목 1");
        assertThat(result.questions()).hasSize(10);
    }

    // ── 전사 정제 (PR-4) ──

    @Test
    @DisplayName("normalize(Transcript): 앞뒤 공백을 털어 그대로 돌려준다 — 줄바꿈은 본문이라 보존한다")
    void normalizeTranscript_trimsEdgesButKeepsLineBreaks() {
        Optional<Transcript> result = ClaudeStudyAssistant.normalize(
                new Transcript("\n  1. 함수의 정의\n2. 호출 규약 [?]  \n", false));

        assertThat(result).isPresent();
        assertThat(result.get().text()).isEqualTo("1. 함수의 정의\n2. 호출 규약 [?]");
        assertThat(result.get().unreadable()).isFalse();
    }

    @Test
    @DisplayName("normalize(Transcript): 읽은 글이 없는데 unreadable도 아니면 빈 결과 — 담을 값이 없다")
    void normalizeTranscript_blankTextWithoutFlag_isEmpty() {
        assertThat(ClaudeStudyAssistant.normalize(new Transcript("   ", false))).isEmpty();
        assertThat(ClaudeStudyAssistant.normalize(new Transcript(null, false))).isEmpty();
        assertThat(ClaudeStudyAssistant.normalize((Transcript) null)).isEmpty();
    }

    @Test
    @DisplayName("normalize(Transcript): 「전혀 못 읽었다」는 빈 글이어도 정상 답이다 — 실패로 접지 않는다")
    void normalizeTranscript_unreadable_isPresentWithEmptyText() {
        Optional<Transcript> result = ClaudeStudyAssistant.normalize(new Transcript(null, true));

        assertThat(result).isPresent();
        assertThat(result.get().text()).isEmpty();
        assertThat(result.get().unreadable()).isTrue();
    }

    @Test
    @DisplayName("transcribe: 키가 없으면 외부 호출 없이 DISABLED · 사진도 없으면 마찬가지")
    void transcribe_whenDisabled_returnsDisabled() {
        var images = List.of(new ClaudeStudyAssistant.ImagePart("image/jpeg", new byte[] {1}));

        assertThat(withKey("not-configured").transcribe(images).failure())
                .isEqualTo(ClaudeStudyAssistant.Failure.DISABLED);
        assertThat(withKey("sk-ant-실값").transcribe(List.of()).failure())
                .isEqualTo(ClaudeStudyAssistant.Failure.DISABLED);
    }

    @Test
    @DisplayName("analyzeRecall: 키가 없으면 외부 호출 없이 DISABLED")
    void analyzeRecall_whenDisabled_returnsDisabled() {
        var result = withKey("not-configured").analyzeRecall(new RecallInput("과목", "범위", "본문"));

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(ClaudeStudyAssistant.Failure.DISABLED);
    }

    @Test
    @DisplayName("generatePlan: 키가 없으면 외부 호출 없이 DISABLED")
    void generatePlan_whenDisabled_returnsDisabled() {
        var result = withKey("not-configured").generatePlan(
                new PlanInput("정보보안기사", "1장", LocalDate.of(2026, 9, 3),
                        LocalDate.of(2026, 12, 3), 120, 5));

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(ClaudeStudyAssistant.Failure.DISABLED);
    }

    // ── planUserPrompt — 후보 날짜를 서버가 계산해 넣는다 ───────────────────

    @Test
    @DisplayName("planUserPrompt: 후보 날짜는 오늘부터 시험 전날까지다 — 시험 당일은 없다")
    void planUserPrompt_listsCandidateDatesUpToTheDayBeforeExam() {
        String prompt = ClaudeStudyAssistant.planUserPrompt(new PlanInput(
                "정보보안기사", "1장 접근통제", LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 6), 120, 5));

        // 후보 항목은 늘 요일 괄호를 달고 온다 — 「2026-09-06」만 보면 [시험일] 줄에도 걸려 판별력이 없다.
        assertThat(prompt).contains("2026-09-03(목)", "2026-09-04(금)", "2026-09-05(토)");
        assertThat(prompt).doesNotContain("2026-09-06("); // 시험날엔 배정하지 않는다
    }

    @Test
    @DisplayName("planUserPrompt: 과목·범위 원문·분·주 N일이 그대로 실린다")
    void planUserPrompt_carriesTheInputs() {
        String prompt = ClaudeStudyAssistant.planUserPrompt(new PlanInput(
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
        String prompt = ClaudeStudyAssistant.planUserPrompt(new PlanInput(
                "정보보안기사", "   ", LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 10), 90, 4));

        assertThat(prompt).contains("범위가 주어지지 않았");
    }

    @Test
    @DisplayName("planUserPrompt: 후보 날짜가 아주 많아도 프롬프트가 폭발하지 않는다(1년치 상한)")
    void planUserPrompt_withYearLongRange_staysBounded() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        String prompt = ClaudeStudyAssistant.planUserPrompt(new PlanInput(
                "과목", "범위", today, today.plusDays(365), 120, 5));

        assertThat(prompt).contains("2026-01-01", "2026-12-31");
        assertThat(prompt.length()).isLessThan(20_000);
    }

    // ── sanitizePlan — 모델 출력을 믿지 않는 방어선(경계 전수) ─────────────

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);   // 목요일
    private static final LocalDate EXAM = LocalDate.of(2026, 10, 3);

    private static List<PlanDay> sanitize(List<PlanDay> days) {
        return ClaudeStudyAssistant.sanitizePlan(days, TODAY, EXAM, 7);
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
        // 2026-09-07(월)~09-13(일)은 한 주다. 주 2일이면 앞의 두 날만 남는다.
        List<PlanDay> kept = ClaudeStudyAssistant.sanitizePlan(List.of(
                day("2026-09-07", "월"), day("2026-09-08", "화"), day("2026-09-09", "수"),
                day("2026-09-13", "일")), TODAY, EXAM, 2);

        assertThat(kept).extracting(PlanDay::date).containsExactly("2026-09-07", "2026-09-08");
    }

    @Test
    @DisplayName("sanitizePlan: 주 경계는 월요일이다 — 일요일과 그 다음 월요일은 서로 다른 주다")
    void sanitizePlan_weekBoundaryIsMonday() {
        // 2026-09-13(일)과 2026-09-14(월)은 다른 주라, 주 1일이어도 둘 다 남는다.
        List<PlanDay> kept = ClaudeStudyAssistant.sanitizePlan(List.of(
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
        assertThat(ClaudeStudyAssistant.sanitizePlan(null, TODAY, EXAM, 5)).isEmpty();
    }
}
