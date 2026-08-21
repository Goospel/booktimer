package com.booktimer.security;

import com.booktimer.user.User;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 사용자의 로그인 세션을 강제로 끊는다 — 비밀번호가 바뀌었을 때 옛 비밀번호로 들어온 세션을 남기지 않기 위함.
 *
 * <p><b>왜 필요한가</b>: 세션은 JVM 메모리가 아니라 {@code SPRING_SESSION} 테이블에 있고(Spring Session JDBC),
 * 비밀번호 해시가 바뀌어도 이미 만들어진 세션 행은 그대로 살아 있다. 그래서 이 정리가 없으면 <b>비밀번호 변경·재설정이
 * 침입자를 쫓아내지 못한다</b> — 비번이 샜다고 판단해 바꾼 사용자에게 가장 필요한 효과가 정확히 빠진다.
 *
 * <p><b>어떻게 찾는가</b>: {@code PRINCIPAL_NAME} 인덱스로 조회한다. principal name은 폼 로그인·OIDC 양쪽 다
 * {@code login_id}로 통일돼 있지만({@link BookTimerOidcUserService}), <b>온보딩 전 첫 세션만 email</b>이고
 * 아이디를 바꾼 계정은 <b>변경 전에 열린 세션이 옛 login_id</b>로 인덱싱돼 있다. 그래서 셋 다 조회해 지운다 —
 * 어느 하나라도 빠지면 그 이름으로 인덱싱된 세션만 살아남아 축출을 통과한다.
 */
@Component
public class SessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionInvalidator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * 이 사용자의 세션을 지운다.
     *
     * @param keepSessionId 남길 세션 id — 비밀번호 <b>변경</b>은 방금 현재 비번을 증명한 본인 창을 남긴다(플래시 메시지도
     *                      그 세션에 실린다). 비밀번호 <b>재설정</b>은 비로그인 흐름이라 남길 창이 없으므로 {@code null}.
     */
    public void invalidate(User user, String keepSessionId) {
        for (String principalName : principalNames(user)) {
            for (String sessionId : sessions.findByPrincipalName(principalName).keySet()) {
                if (!sessionId.equals(keepSessionId)) {
                    sessions.deleteById(sessionId);
                }
            }
        }
    }

    /**
     * 이 사용자의 세션이 인덱싱될 수 있는 이름들 — login_id(온보딩 후)·email(온보딩 전 첫 세션)·
     * previous_login_id(아이디를 바꾸기 <b>전에</b> 열린 세션).
     *
     * <p>옛 아이디를 빠뜨리면 그 세션만 살아남는다 — 브리지 조회({@code CurrentUserService})가 옛 핸들
     * principal을 계속 해석해 주므로 그 세션은 <b>정상 동작하는 채로</b> 남고, 비밀번호 변경·재설정이
     * 이 클래스의 존재 이유(침입자 축출)를 잃는다.
     */
    private static List<String> principalNames(User user) {
        List<String> names = new ArrayList<>(3);
        if (user.getLoginId() != null) {
            names.add(user.getLoginId());
        }
        if (user.getEmail() != null) {
            names.add(user.getEmail());
        }
        if (user.getPreviousLoginId() != null) {
            names.add(user.getPreviousLoginId());
        }
        return names;
    }
}
