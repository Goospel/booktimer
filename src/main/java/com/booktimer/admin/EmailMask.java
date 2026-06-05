package com.booktimer.admin;

/**
 * 운영 화면용 email 마스킹 (admin-data-lookup-design §3).
 *
 * <p>최소 노출 원칙: local part 첫 글자만 남기고 나머지를 가린다. 도메인은 가입 분포 파악에 유용해
 * 보존한다. 비정상 입력(null·'@' 없음)은 식별 불가한 {@code ***}로 안전 처리한다.
 *
 * <pre>
 *   goospel@gmail.com → g***@gmail.com
 *   ab@y.com          → a***@y.com
 *   a@x.com           → *@x.com      (local 1글자)
 *   null / "noat"     → ***
 * </pre>
 */
public final class EmailMask {

    private EmailMask() {
    }

    /** email을 마스킹한 표시 문자열로 변환한다. 원문은 호출부가 따로 보관(토글 노출). */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at < 1) {
            // '@'가 없거나 local part가 비어있음 → 식별 불가 안전값
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at); // '@' 포함
        String maskedLocal = local.length() <= 1 ? "*" : local.charAt(0) + "***";
        return maskedLocal + domain;
    }
}
