package com.booktimer.user;

/**
 * 사용자의 인증 출처.
 *
 * <p>{@link #LOCAL} 은 이메일/비밀번호로 직접 가입한 계정(비밀번호 보유), {@link #GOOGLE} 등은
 * 소셜 로그인으로 만들어진 계정(비밀번호 없음, provider가 신원을 보증)이다. 이 구분이 있어야
 * "비밀번호 변경은 LOCAL 계정만", "소셜 계정은 provider가 인증" 같은 규칙을 도메인에서 표현할 수 있다.
 */
public enum AuthProvider {
    /** 이메일/비밀번호 직접 가입 — 비밀번호(해시)를 가진다. */
    LOCAL,
    /** 구글 소셜 로그인 — 비밀번호 없음, 구글이 신원을 보증. */
    GOOGLE,
    /**
     * 토스 앱인토스(미니앱)에서 시작한 계정 — 비밀번호 없음, 신원은 {@code users.toss_user_key}(토스 userKey).
     *
     * <p><b>기존 계정을 토스에 연결</b>한 경우엔 authProvider를 바꾸지 않는다(LOCAL/GOOGLE 유지) —
     * 이 값은 "원 가입 경로"의 의미이고, 비밀번호 변경 가능 여부 같은 기존 규칙이 여기 걸려 있어
     * 바꾸면 회귀한다. "토스로 로그인 가능한가"는 toss_user_key 보유 여부가 답한다(설계 §2.2).
     */
    TOSS
}
