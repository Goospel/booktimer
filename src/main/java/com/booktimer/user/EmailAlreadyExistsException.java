package com.booktimer.user;

/**
 * 이미 가입된 이메일로 다시 가입을 시도할 때 발생.
 *
 * <p>이메일은 유니크 키(uk_users_email)다. DB 제약 위반(DataIntegrityViolationException)이
 * 500으로 새어 나가기 전에, 등록 서비스가 미리 확인해 이 예외로 알린다 — 상위(컨트롤러)는
 * 이를 사용자에게 친절한 폼 에러로 바꾼다.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("email already registered: " + email);
    }
}
