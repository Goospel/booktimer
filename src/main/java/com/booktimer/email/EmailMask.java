package com.booktimer.email;

/**
 * 로그용 이메일 마스킹 — 평문 이메일을 로그에 남기지 않기 위한 단일 출처.
 *
 * <p>로그는 앱보다 접근 통제가 느슨하고(수집·백업 파이프라인을 타면 보존 범위가 통제를 벗어난다),
 * 이메일은 그 자체로 가입자 식별자다. 그래서 <b>User가 손에 있으면 {@code userId=}를 찍고</b>,
 * 이메일밖에 없는 경로(발송 실패·억제 등록 등)에서만 이 마스크를 쓴다.
 *
 * <p>도메인은 남긴다 — "특정 도메인 전체 실패" 같은 발송 장애 진단에 필요한 최소 정보이고,
 * 도메인만으로는 개인이 식별되지 않는다.
 */
public final class EmailMask {

    private static final String INVALID = "(invalid)";

    private EmailMask() {
    }

    /**
     * {@code reader@booktimer.com} → {@code r***@booktimer.com}.
     *
     * <p>{@code ***}는 <b>고정 길이</b>다 — 로컬파트 길이에 맞추면 그 길이가 그대로 단서가 된다.
     * null·공백·{@code @} 없음·로컬파트 빈 값은 {@code (invalid)} — <b>가리지 못하면 아예 안 찍는다</b>
     * (원문 폴백은 마스킹을 무의미하게 만든다).
     */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return INVALID;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return INVALID;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
