package com.booktimer.user;

/**
 * 이미 사용 중인 닉네임으로 가입/변경을 시도할 때 발생.
 *
 * <p>닉네임은 유니크 키(uk_users_nickname)이자 검색·프로필 핸들이다. DB 제약 위반
 * (DataIntegrityViolationException)이 500으로 새어 나가기 전에, 서비스가 미리 확인해 이 예외로
 * 알린다 — 상위(컨트롤러)는 이를 사용자에게 친절한 폼 에러로 바꾼다.
 *
 * <p>소셜 로그인은 예외 대상이 아니다 — provider가 준 표시 이름이 충돌하면 로그인을 거부할 수 없으므로
 * {@link NicknameAllocator}로 자동 유일화한다(거부가 아니라 회피).
 */
public class NicknameAlreadyExistsException extends RuntimeException {

    public NicknameAlreadyExistsException(String nickname) {
        super("nickname already in use: " + nickname);
    }
}
