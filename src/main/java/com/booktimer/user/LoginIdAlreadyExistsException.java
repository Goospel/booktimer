package com.booktimer.user;

/**
 * 이미 쓰이는 login_id(공개 핸들)를 온보딩에서 고르려 할 때 발생.
 *
 * <p>login_id는 유니크 키(uk_users_login_id)이자 <b>불변</b> 공개 핸들이다. DB 제약 위반
 * (DataIntegrityViolationException)이 500으로 새기 전에, 온보딩 서비스가 미리 확인해 이 예외로
 * 알린다 — 상위(컨트롤러)는 이를 사용자에게 친절한 폼 에러로 바꿔 다른 아이디를 받는다.
 */
public class LoginIdAlreadyExistsException extends RuntimeException {

    public LoginIdAlreadyExistsException(String loginId) {
        super("login_id already taken: " + loginId);
    }
}
