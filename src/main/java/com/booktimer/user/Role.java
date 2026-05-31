package com.booktimer.user;

/**
 * 사용자 권한.
 *
 * <p>{@link #USER} 일반 사용자, {@link #ADMIN} 운영자. Spring Security 권한 매핑 시
 * {@code ROLE_} 접두는 보안 설정 단계에서 부여한다(엔티티는 순수 도메인 값만 보관).
 */
public enum Role {
    USER,
    ADMIN
}
