package com.booktimer.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 영속성 저장소.
 *
 * <p>이메일은 로그인 식별자이자 유니크 키(uk_users_email)다. 닉네임은 더 이상 유니크가 아니다 — 단순 표시
 * 이름이라 중복을 허용한다. 식별·검색의 공개 핸들은 불변의 login_id로 옮겨간다(PR-3, login-id-design.md).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * 닉네임으로 사용자 조회. <b>닉네임은 더 이상 유니크가 아니라</b>(중복 허용) 1:1 핸들이 아니다 —
     * 프로필·검색 핸들은 PR-3에서 login_id로 옮겨간다. 그때까지 임시로 유지한다(첫 일치 1명).
     */
    Optional<User> findByNickname(String nickname);

    /**
     * 닉네임 부분일치 검색(대소문자 무시), 닉네임 오름차순, <b>최대 20명</b>.
     * 결과 상한(Top20)으로 열거·크롤링을 완화한다(sns-design §7.3·§9). 최소 길이 가드는 서비스가 담당.
     * 검색 핸들은 PR-3에서 login_id로 옮겨간다.
     */
    java.util.List<User> findTop20ByNicknameContainingIgnoreCaseOrderByNicknameAsc(String nickname);

    boolean existsByEmail(String email);

    /** 정규화된 login_id가 이미 쓰이는지 — 온보딩에서 공개 핸들 확정 전 유니크 사전 확인(uk_users_login_id). */
    boolean existsByLoginId(String loginId);
}
