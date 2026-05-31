package com.booktimer.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * 서비스 사용자.
 *
 * <p>설계(domain-design.md): User(1) ↔ ReadingTimer(1), User(1) ↔ ReadingSession(N).
 * 이 증분은 User 자체의 식별/인증/표시 속성만 담는다. ReadingTimer 연관(@OneToOne)은
 * 다음 증분에서 연결한다.
 *
 * <p>비밀번호는 평문을 받지 않는다 — 해싱(BCrypt)은 서비스 책임이고, 엔티티는
 * 이미 해시된 문자열({@code passwordHash})만 보관한다.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {

    /** 간단한 이메일 형식 검증(공백 없는 local@domain.tld). 정밀 검증은 서비스/검증 계층 몫. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /** BCrypt 등으로 이미 해시된 비밀번호. 평문 저장 금지. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    /** IANA 타임존 ID(예: "Asia/Seoul"). 일일 누적 경계(자정) 계산 기준. */
    @Column(nullable = false)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected User() {
        // JPA
    }

    private User(String email, String passwordHash, String nickname, String timezone, Role role) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email is malformed: " + email);
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname must not be blank");
        }
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("timezone must not be blank");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("timezone is not a valid IANA zone id: " + timezone, e);
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.timezone = timezone;
        this.role = role;
    }

    /**
     * 사용자를 생성한다.
     *
     * @param email        이메일(형식 검증)
     * @param passwordHash 이미 해시된 비밀번호(평문 금지)
     * @param nickname     표시용 닉네임
     * @param timezone     IANA 타임존 ID(예: "Asia/Seoul")
     * @param role         권한
     * @throws IllegalArgumentException 검증 실패 시
     */
    public static User of(String email, String passwordHash, String nickname,
                          String timezone, Role role) {
        return new User(email, passwordHash, nickname, timezone, role);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getTimezone() {
        return timezone;
    }

    public Role getRole() {
        return role;
    }
}
