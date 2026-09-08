package com.booktimer.study;

/**
 * 공부 화면 AI 어댑터들이 <b>공유하는 결과 형(型)</b>.
 *
 * <p>원래 {@link ClaudeStudyAssistant} 안에 중첩돼 있었다. 2026-09-08에 일정 생성이
 * {@link GeminiStudyPlanner}로 떨어져 나가면서 공급자가 둘이 됐고, Gemini 어댑터가
 * {@code ClaudeStudyAssistant.AiResult}를 돌려주는 모양은 읽는 사람을 속인다 — 그래서 여기로 꺼냈다.
 *
 * <p>바깥 이름만 바뀌고 {@code StudyAi.AiResult} · {@code StudyAi.Failure}로 <b>import 형태는 같다</b>.
 * 인스턴스로 만들 것이 없으므로 생성자를 막아 둔다.
 */
public final class StudyAi {

    private StudyAi() {
    }

    /** 실패의 갈래 — 화면 문구와 HTTP 상태가 여기서 갈린다(호출부가 옮긴다). */
    public enum Failure {
        /** 키가 없다(외부에 나가지도 않았다). */
        DISABLED,
        /** 429 — 잠시 후 다시. */
        RATE_LIMITED,
        /** 요청 자체가 거부됐다(사진 형식 등 — 사진 경로가 붙는 판에서 주로 쓰인다). */
        BAD_INPUT,
        /** 그 밖의 장애·파싱 실패·잘린 응답. */
        UNAVAILABLE
    }

    /**
     * 성공값 또는 실패 사유 — 둘 중 하나만 채워진다.
     *
     * <p>{@code Optional}이 아닌 이유는 <b>왜 실패했는가</b>가 화면 문구를 가르기 때문이다(꺼짐 · 혼잡 ·
     * 장애가 서로 다른 안내다).
     */
    public record AiResult<T>(T value, Failure failure) {

        public static <T> AiResult<T> ok(T value) {
            return new AiResult<>(value, null);
        }

        public static <T> AiResult<T> fail(Failure failure) {
            return new AiResult<>(null, failure);
        }

        public boolean ok() {
            return failure == null && value != null;
        }
    }
}
