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

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
