package com.booktimer.user;

import com.booktimer.common.BaseTimeEntity;
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
public class User extends BaseTimeEntity {

    /** 간단한 이메일 형식 검증(공백 없는 local@domain.tld). 정밀 검증은 서비스/검증 계층 몫. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /**
     * BCrypt 등으로 이미 해시된 비밀번호. 평문 저장 금지.
     * <b>LOCAL 계정만 보유</b> — 소셜(OAuth) 계정은 비밀번호가 없어 {@code null}이다.
     */
    @Column(nullable = true)
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    /** IANA 타임존 ID(예: "Asia/Seoul"). 일일 누적 경계(자정) 계산 기준. */
    @Column(nullable = false)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** 인증 출처(LOCAL/소셜). 기존 행은 LOCAL로 채워지도록 컬럼 기본값을 둔다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'LOCAL'")
    private AuthProvider authProvider;

    protected User() {
        // JPA
    }

    private User(String email, String passwordHash, String nickname, String timezone,
                 Role role, AuthProvider authProvider) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email is malformed: " + email);
        }
        if (authProvider == null) {
            throw new IllegalArgumentException("authProvider must not be null");
        }
        // LOCAL 계정은 비밀번호 해시를 반드시 가진다. 소셜 계정은 비밀번호가 없다(null 허용).
        if (authProvider == AuthProvider.LOCAL && (passwordHash == null || passwordHash.isBlank())) {
            throw new IllegalArgumentException("passwordHash must not be blank for LOCAL account");
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
        this.authProvider = authProvider;
    }

    /**
     * 이메일/비밀번호로 직접 가입한 LOCAL 사용자를 생성한다.
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
        return new User(email, passwordHash, nickname, timezone, role, AuthProvider.LOCAL);
    }

    /**
     * 소셜 로그인(OAuth)으로 만들어진 비밀번호 없는 사용자를 생성한다. 신원은 provider가 보증한다.
     *
     * @param provider 소셜 provider(LOCAL은 불가 — 비밀번호 없는 LOCAL은 모순)
     * @throws IllegalArgumentException provider가 null/LOCAL이거나 공통 필드 검증 실패 시
     */
    public static User ofOAuth(String email, String nickname, String timezone,
                               Role role, AuthProvider provider) {
        if (provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("OAuth account provider must not be LOCAL");
        }
        return new User(email, null, nickname, timezone, role, provider);
    }

    /**
     * 사용자가 변경 가능한 프로필 속성(닉네임, 타임존)을 갱신한다.
     * 식별/인증 속성(email, passwordHash, role)은 여기서 바꾸지 않는다.
     *
     * @param nickname 새 표시 닉네임(공백 불가)
     * @param timezone 새 IANA 타임존 ID(예: "Asia/Seoul")
     * @throws IllegalArgumentException 닉네임이 공백이거나 타임존이 유효한 IANA ID가 아닌 경우
     */
    public void updateProfile(String nickname, String timezone) {
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
        this.nickname = nickname;
        this.timezone = timezone;
    }

    /**
     * 비밀번호를 <b>이미 해시된</b> 새 값으로 교체한다. 평문은 받지 않는다 — 현재 비밀번호 확인과
     * 평문 해싱은 서비스({@code AccountService})의 책임이고, 엔티티는 해시 문자열만 보관한다.
     *
     * @param newPasswordHash 새 비밀번호의 해시(공백 불가)
     * @throws IllegalArgumentException 해시가 비어있는 경우
     */
    public void changePassword(String newPasswordHash) {
        if (!isLocalAccount()) {
            throw new IllegalStateException("social account has no password to change: " + authProvider);
        }
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        this.passwordHash = newPasswordHash;
    }

    /** 이메일/비밀번호로 가입한 LOCAL 계정인가(=비밀번호를 가진 계정). */
    public boolean isLocalAccount() {
        return authProvider == AuthProvider.LOCAL;
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

    public AuthProvider getAuthProvider() {
        return authProvider;
    }
}
