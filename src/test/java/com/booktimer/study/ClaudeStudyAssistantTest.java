package com.booktimer.study;

import com.booktimer.study.ClaudeStudyAssistant.RecallAnalysis;
import com.booktimer.study.ClaudeStudyAssistant.RecallInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertThat(ClaudeStudyAssistant.normalize(null)).isEmpty();
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

    @Test
    @DisplayName("analyzeRecall: 키가 없으면 외부 호출 없이 DISABLED")
    void analyzeRecall_whenDisabled_returnsDisabled() {
        var result = withKey("not-configured").analyzeRecall(new RecallInput("과목", "범위", "본문"));

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(ClaudeStudyAssistant.Failure.DISABLED);
    }
}
