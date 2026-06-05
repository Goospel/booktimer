package com.booktimer.user;

/**
 * 소셜(비밀번호 없는) 계정 탈퇴 시, 재확인용으로 입력한 @핸들(login_id)이 본인 것과 일치하지 않을 때 던진다.
 *
 * <p>소셜 계정은 비밀번호가 없어 비번 재인증을 못 한다. 대신 되돌릴 수 없는 파괴적 동작(탈퇴) 직전에
 * 본인 공개 @핸들을 정확히 입력하게 해 우발적·CSRF성 삭제를 막는다(GitHub의 "저장소 이름 입력" 패턴).
 * 컨트롤러가 이 예외를 잡아 "아이디가 일치하지 않습니다" 같은 안내로 변환한다 — 인증 세션 자체는 유효하므로
 * 보안 필터가 아니라 도메인 확인 실패다.
 */
public class AccountDeletionConfirmationException extends RuntimeException {

    public AccountDeletionConfirmationException() {
        super("account deletion confirmation handle does not match");
    }
}
