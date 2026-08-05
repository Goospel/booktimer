package com.booktimer.auth;

/**
 * 토스 로그인으로 확인한 사용자 신원.
 *
 * @param userKey 앱 단위 사용자 식별자 — <b>우리 쪽 신원의 유일한 근거</b>다(항상 존재)
 * @param email   토스 계정 이메일. 콘솔 동의항목·토스 계정 상태에 따라 <b>{@code null}일 수 있다</b> —
 *                그래서 이메일을 신원으로 쓰지 않고, 없거나 충돌하면 합성 주소로 폴백한다(설계 §2.2)
 */
public record TossUserInfo(String userKey, String email) {
}
