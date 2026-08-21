package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 강제 만료 통합 테스트 (실제 세션 저장소 + H2).
 *
 * <p>왜 실 저장소인가: 세션은 JVM 메모리가 아니라 {@code SPRING_SESSION} 테이블에 있고(Spring Session JDBC),
 * 삭제 대상을 찾는 키가 {@code PRINCIPAL_NAME} 인덱스다. mock으로는 "인덱스가 실제로 그 이름으로 채워지는가"를
 * 검증할 수 없어, 배선이 어긋나도 통과하는 무의미한 테스트가 된다.
 *
 * <p>principal name은 폼 로그인·OIDC 양쪽 다 {@code login_id}로 통일돼 있다(BookTimerOidcUserService) —
 * 단 온보딩 전 첫 세션만 email이라, 그 창에 걸린 세션도 함께 지워야 구멍이 없다.
 *
 * <p>테스트마다 principal 이름을 다르게 써서 서로 간섭하지 않는다 — 세션 저장소는 REQUIRES_NEW로
 * 즉시 커밋해서 {@code @Transactional} 롤백이 듣지 않는다.
 */
@SpringBootTest
class SessionInvalidatorTest {

    @Autowired
    private SessionInvalidator invalidator;
    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    /** principal 이름으로 인덱싱된 세션을 하나 만들고 그 id를 돌려준다. */
    private String newSession(String principalName) {
        return create(sessions, principalName);
    }

    // 와일드카드를 타입변수로 캡처해야 save(S)가 컴파일된다(저장소의 세션 구현 타입에 묶이지 않기 위함).
    private static <S extends Session> String create(SessionRepository<S> repo, String principalName) {
        S session = repo.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
        repo.save(session);
        return session.getId();
    }

    private boolean alive(String sessionId) {
        return sessions.findById(sessionId) != null;
    }

    private static User user(String email, String loginId) {
        User u = User.of(email, "hash", "독자", "Asia/Seoul", Role.USER);
        if (loginId != null) {
            u.assignLoginId(loginId);
        }
        return u;
    }

    @Test
    @DisplayName("지정한 세션 하나만 남기고 그 사용자의 나머지 세션을 모두 지운다 — 비번 변경 시 내 창은 유지")
    void keepsOnlyTheGivenSession() {
        String keep = newSession("keeper01");
        String other1 = newSession("keeper01");
        String other2 = newSession("keeper01");

        invalidator.invalidate(user("keeper@booktimer.com", "keeper01"), keep);

        assertThat(alive(keep)).isTrue();
        assertThat(alive(other1)).isFalse();
        assertThat(alive(other2)).isFalse();
    }

    @Test
    @DisplayName("남길 세션이 없으면(null) 그 사용자의 세션을 전부 지운다 — 비번 재설정은 비로그인 흐름")
    void nullKeepRemovesEverySession() {
        String s1 = newSession("purge01");
        String s2 = newSession("purge01");

        invalidator.invalidate(user("purge@booktimer.com", "purge01"), null);

        assertThat(alive(s1)).isFalse();
        assertThat(alive(s2)).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 세션은 건드리지 않는다 — 한 사람의 비번 변경이 남을 로그아웃시키면 안 된다")
    void leavesOtherUsersAlone() {
        String mine = newSession("mine01");
        String stranger = newSession("stranger01");

        invalidator.invalidate(user("mine@booktimer.com", "mine01"), null);

        assertThat(alive(mine)).isFalse();
        assertThat(alive(stranger)).isTrue();
    }

    @Test
    @DisplayName("온보딩 전(login_id 없음) 세션은 email로 인덱싱된다 — 그 창의 세션도 지운다")
    void removesSessionsIndexedByEmailWhenLoginIdMissing() {
        String s = newSession("pre@booktimer.com");

        invalidator.invalidate(user("pre@booktimer.com", null), null);

        assertThat(alive(s)).isFalse();
    }

    @Test
    @DisplayName("login_id가 있어도 email로 인덱싱된 옛 세션까지 함께 지운다 — 온보딩을 걸친 사용자의 구멍 차단")
    void removesBothLoginIdAndEmailIndexedSessions() {
        String byEmail = newSession("both@booktimer.com");
        String byLoginId = newSession("both01");

        invalidator.invalidate(user("both@booktimer.com", "both01"), null);

        assertThat(alive(byEmail)).isFalse();
        assertThat(alive(byLoginId)).isFalse();
    }

    @Test
    @DisplayName("아이디를 바꾼 계정은 옛 아이디로 인덱싱된 세션까지 지운다 — 비번을 바꿔도 침입자가 안 쫓겨나면 안 된다")
    void removesSessionsIndexedByPreviousLoginId() {
        // 아이디 변경 전에 열린 세션의 principal은 옛 핸들이고, 브리지 조회 덕에 그 세션은 계속 잘 동작한다.
        // 그래서 여기서 안 지우면 비밀번호 재설정이 정확히 그 세션을 남긴다.
        String byOldHandle = newSession("prevold01");
        User u = user("prev@booktimer.com", "prevold01");
        u.changeLoginId("prevnew01");

        invalidator.invalidate(u, null);

        assertThat(alive(byOldHandle)).isFalse();
    }

    @Test
    @DisplayName("지울 세션이 없어도 조용히 끝난다")
    void noSessionsIsNotAnError() {
        invalidator.invalidate(user("nobody@booktimer.com", "nobody01"), null);
    }
}
