package com.booktimer.admin;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;

import java.time.Instant;

/**
 * 관리자 사용자 목록의 한 행 (admin-data-lookup-design §2.1).
 *
 * <p><b>비밀번호 해시는 담지 않는다</b>(민감값 비노출). email은 원문과 마스킹본을 함께 담아
 * 화면이 기본은 마스킹, 클릭 시 원문을 보이게 한다(§3).
 *
 * @param loginId     공개 @핸들(불변 식별 키 — 상세 링크에 쓰임)
 * @param nickname    표시 이름
 * @param email       원문(토글 시 노출)
 * @param emailMasked 마스킹본(기본 표시)
 * @param provider    인증 출처(LOCAL/소셜)
 * @param createdAt   가입 시각
 * @param onboarded     온보딩 완료 여부
 * @param emailVerified 가입 이메일 검증 여부
 * @param role          역할(USER/ADMIN)
 */
public record AdminUserRow(
        String loginId,
        String nickname,
        String email,
        String emailMasked,
        AuthProvider provider,
        Instant createdAt,
        boolean onboarded,
        boolean emailVerified,
        Role role) {

    /** User 엔티티에서 행을 만든다 — email은 마스킹본을 함께 계산한다. */
    public static AdminUserRow from(com.booktimer.user.User u) {
        return new AdminUserRow(
                u.getLoginId(),
                u.getNickname(),
                u.getEmail(),
                EmailMask.mask(u.getEmail()),
                u.getAuthProvider(),
                u.getCreatedAt(),
                u.isOnboarded(),
                u.isEmailVerified(),
                u.getRole());
    }
}
