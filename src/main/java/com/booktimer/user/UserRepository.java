package com.booktimer.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 영속성 저장소.
 *
 * <p>이메일은 로그인 식별자이자 유니크 키(uk_users_email)다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
