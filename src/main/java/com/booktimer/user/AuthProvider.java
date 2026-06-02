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
    GOOGLE
}
