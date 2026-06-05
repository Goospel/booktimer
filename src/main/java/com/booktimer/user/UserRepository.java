package com.booktimer.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 영속성 저장소.
 *
 * <p>이메일은 로그인 식별자이자 유니크 키(uk_users_email)다(인증 컷오버 전까지). 닉네임은 더 이상 유니크가
 * 아니다 — 단순 표시 이름이라 중복을 허용한다. <b>식별·검색의 공개 핸들은 불변의 login_id</b>다(PR-3 컷오버
 * 완료, login-id-design.md §7): 프로필 조회·검색·팔로우/차단/신고 대상 식별이 모두 login_id 기준이다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * login_id(공개 @핸들)로 사용자 조회. login_id는 유니크·불변이라 1:1 식별의 정식 키다 —
     * 프로필({@code /u/{loginId}})·팔로우/차단/신고 대상 식별이 이걸로 이뤄진다.
     */
    Optional<User> findByLoginId(String loginId);

    /**
     * login_id 부분일치 검색(대소문자 무시), login_id 오름차순, <b>최대 20명</b>.
     * 검색은 닉네임이 아니라 <b>공개 @핸들(login_id) 기준</b>이다(인스타/X 모델). 결과 상한(Top20)으로
     * 열거·크롤링을 완화한다(sns-design §7.3·§9). 최소 길이 가드는 서비스가 담당.
     */
    java.util.List<User> findTop20ByLoginIdContainingIgnoreCaseOrderByLoginIdAsc(String loginId);

    boolean existsByEmail(String email);

    /** 정규화된 login_id가 이미 쓰이는지 — 온보딩에서 공개 핸들 확정 전 유니크 사전 확인(uk_users_login_id). */
    boolean existsByLoginId(String loginId);

    /** 역할별 사용자 수 — 운영 통계에서 "가입자 수"는 {@code Role.USER}만 세어 ADMIN이 지표를 부풀리지 않게 한다. */
    long countByRole(Role role);

    /** 역할 + 온보딩 완료 여부별 사용자 수 — 운영 통계의 "온보딩 완료자" 카드. */
    long countByRoleAndOnboarded(Role role, boolean onboarded);
}
