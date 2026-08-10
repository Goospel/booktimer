package com.booktimer.auth;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/** {@link ApiToken} 영속성 저장소 — 조회 키는 언제나 평문이 아니라 해시다. */
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    /**
     * 해시로 토큰을 찾는다 — <b>user를 fetch join으로 함께 초기화</b>한다.
     *
     * <p>파생 쿼리로 두면 {@code ApiToken.user}(LAZY)가 프록시로 남아, 트랜잭션 밖에서 이 user를 쓰는
     * {@code BearerTokenFilter}(DispatcherServlet 앞 = OSIV도 없는 지점)가
     * {@code LazyInitializationException}으로 터진다(미니앱 Bearer API 전량 500).
     */
    @Query("select t from ApiToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<ApiToken> findByTokenHash(String tokenHash);

    /** 계정 삭제 시 FK 자식 정리용(users를 참조하므로 유저보다 먼저 지워야 한다 — T-029). */
    void deleteByUser(User user);
}
