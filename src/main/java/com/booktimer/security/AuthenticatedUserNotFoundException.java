package com.booktimer.security;

/**
 * 인증 주체(principal)는 있는데 그 이름으로 도메인 사용자를 찾을 수 없다 — {@link CurrentUserService#resolve}의
 * 해석 실패다. 계정이 삭제된 뒤 남은 세션 등 <b>서버 결함</b>이므로 전역 처리기가 500으로 응답해야 한다.
 *
 * <p><b>{@code IllegalStateException}을 상속하지 않는 것이 이 클래스의 존재 이유다.</b> 컨트롤러 두 곳
 * ({@code StudyAiAccessApiController}·{@code MiniappHandleApiController})이 클래스 범위
 * {@code @ExceptionHandler(IllegalStateException.class)}로 <i>도메인 전이 위반</i>을 409로 옮기는데,
 * 해석 실패가 같은 타입이면 그 핸들러에 걸려 「이미 신청했다」·「이미 아이디가 있어요」라는 <b>거짓 안내</b>가
 * 된다. 하위 타입으로 만들면 그대로 잡히므로 {@link RuntimeException}에서 직접 파생한다.
 */
public class AuthenticatedUserNotFoundException extends RuntimeException {

    public AuthenticatedUserNotFoundException(String principalName) {
        super("authenticated user not found: " + principalName);
    }
}
