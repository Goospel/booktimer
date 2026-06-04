package com.booktimer.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 영속성 저장소.
 *
 * <p>이메일은 로그인 식별자이자 유니크 키(uk_users_email)다. 닉네임도 유니크 키(uk_users_nickname)이자
 * 검색·프로필 핸들이다 — 가입/변경 전에 {@link #existsByNickname}로 중복을 미리 확인한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 닉네임으로 사용자 조회 — 프로필 페이지 핸들(유니크 uk_users_nickname, 1:1). */
    Optional<User> findByNickname(String nickname);

    /**
     * 닉네임 부분일치 검색(대소문자 무시), 닉네임 오름차순, <b>최대 20명</b>.
     * 결과 상한(Top20)으로 열거·크롤링을 완화한다(sns-design §7.3·§9). 최소 길이 가드는 서비스가 담당.
     */
    java.util.List<User> findTop20ByNicknameContainingIgnoreCaseOrderByNicknameAsc(String nickname);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
