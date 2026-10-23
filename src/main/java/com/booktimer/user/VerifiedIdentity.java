package com.booktimer.user;

/**
 * 탈퇴 재확인에서 제공자가 검증해 준 신원(REQ-83). APPLE · KAKAO는 {@code sub}만, GOOGLE은 판정 재료로 이메일 · hd까지 쓴다.
 *
 * @param sub   검증된 토큰의 sub
 * @param email GOOGLE일 때 검증된 이메일(그 밖은 null)
 * @param hd    GOOGLE일 때 워크스페이스 도메인(없으면 null)
 */
public record VerifiedIdentity(String sub, String email, String hd) {
}
