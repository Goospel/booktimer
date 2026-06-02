package com.booktimer.user;

/**
 * 현재 비밀번호 확인에 실패했을 때 던진다 (비밀번호 변경·회원 탈퇴 등 재인증이 필요한 작업).
 *
 * <p>컨트롤러는 이 예외를 잡아 "비밀번호가 올바르지 않습니다" 같은 친절한 메시지로 변환한다 —
 * 인증/인가 자체(로그인 세션)는 유효하므로 보안 필터의 영역이 아니라 도메인 검증 실패다.
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException() {
        super("current password does not match");
    }
}
