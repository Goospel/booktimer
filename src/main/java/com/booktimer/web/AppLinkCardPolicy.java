package com.booktimer.web;

/**
 * 웹 설정 「아이폰 앱 연결」 카드를 누구에게 보이나(v1 REQ-73). 서버 PR은 머지 = 운영 배포라, 스토어에 없는 앱의 카드가
 * 출시까지 모든 웹 사용자에게 보이지 않게 허용 목록({@code booktimer.app.link-card-users})으로 연다.
 *
 * <p>값은 쉼표로 나눈 사용자 id 목록 · {@code *} = 모두 · 숫자가 아닌 칸은 무시한다(기본 {@code none} = 아무도).
 * <b>이미 Apple이 연결된 사용자는 목록과 무관하게 보인다</b> — 연결 해제(REQ-41 복구 수단)를 숨기지 않기 위해서다.
 * 코드 발급 POST도 같은 판정을 쓴다. 연결 해제 POST는 판정하지 않는다.
 */
public final class AppLinkCardPolicy {

    private AppLinkCardPolicy() {
    }

    public static boolean visible(long userId, boolean appleLinked, String allow) {
        if (appleLinked) {
            return true;
        }
        if (allow == null) {
            return false;
        }
        for (String entry : allow.split(",")) {
            String e = entry.strip();
            if (e.equals("*") || (e.matches("\\d{1,18}") && Long.parseLong(e) == userId)) {
                return true;
            }
        }
        return false;
    }
}
