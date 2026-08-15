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
import java.util.Locale;
import java.util.Set;
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

    /** login_id 형식: 영소문자/숫자/언더스코어, 3~20자. 입력은 소문자로 정규화한 뒤 검증한다. */
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z0-9_]{3,20}$");

    /**
     * 닉네임 최대 길이 — 웹 폼({@code @Size})·미니앱 API가 공유하는 <b>단일 출처</b>.
     *
     * <p>예전엔 규칙이 채널마다 달랐다(설정 30 / 온보딩 50 / 도메인 무제한): 40자로 온보딩한 사람은
     * 설정 화면에서 영영 닉네임을 바꿀 수 없었다. 검사를 도메인으로 내려 그 어긋남을 없앤다.
     */
    public static final int NICKNAME_MAX_LENGTH = 30;

    /** 로그인 아이디로 쓸 수 없는 예약어(혼동·사칭·경로 충돌 방지). 정규화(소문자) 후 비교한다. */
    private static final Set<String> RESERVED_LOGIN_IDS = Set.of(
            "admin", "administrator", "root", "me", "api", "system", "support", "help", "booktimer");

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

    /**
     * 첫 진입 시 초기 설정(온보딩 — 타이머 초기값/증가값/상한 직접 지정) 완료 여부.
     * 신규 가입자는 {@code false}로 시작해 온보딩 페이지로 유도되고, 완료하면 {@code true}가 된다.
     * 기존 사용자는 마이그레이션에서 {@code true}로 백필돼 온보딩을 강요받지 않는다.
     */
    @Column(nullable = false)
    private boolean onboarded = false;

    /**
     * 가입 이메일이 검증되었는지(이메일 인프라 1단계 PR-B). 신규 가입은 {@code false}로 시작해 인증 메일의
     * 링크({@code /verify-email?token=})를 따라야 {@code true}가 된다. 기존 사용자는 마이그레이션에서
     * {@code true}로 백필돼(grandfather — 이미 활성·신뢰) 인증을 강요받지 않는다.
     *
     * <p>핵심 게이트: <b>미검증 LOCAL 계정은 OAuth 자동연결 대상에서 제외</b>해 pre-hijacking을 막는다
     * (N-053). 미검증이어도 로그인·사용은 정상 허용한다(입문자 마찰 최소 — thesis).
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /**
     * 책BTI "다시 분석"(강제 재생성 = 매번 LLM 호출)을 <b>하루에 몇 번 했는지</b>와 그 카운트가 속한 날짜.
     * 악의적 반복 클릭이 LLM을 남용해 서버에 부담을 주는 것을 막는 일일 횟수 제한의 상태다
     * ({@link #tryConsumePersonalityRefresh}). "하루"는 사용자 타임존 기준이라(자정 경계) 날짜를 함께 들고 있다가,
     * 날짜가 바뀌면 카운트를 0으로 리셋한다. 인메모리 카운터가 아니라 DB에 두는 이유는 인스턴스가 여럿(ECS)이라도
     * 한도가 일관되게 적용되고 재기동에도 유지되게 하기 위함이다.
     */
    @Column(name = "personality_refresh_count", nullable = false)
    private int personalityRefreshCount = 0;

    @Column(name = "personality_refresh_date")
    private java.time.LocalDate personalityRefreshDate;

    /**
     * 로그인/식별용 아이디이자 <b>공개 @핸들</b>(인스타/X 모델, 설계: login-id-design.md). email 대신 이걸로
     * 로그인·식별하고, 검색·프로필 URL({@code /u/{loginId}})·팔로우 대상 식별도 이 값 기준이다(PR-3 컷오버).
     * 형식은 {@link #LOGIN_ID_PATTERN}(소문자 영숫자·언더스코어 3~20자). 표시 이름(nickname)과 달리 <b>불변</b>이다
     * ({@link #assignLoginId}). 로컬 가입은 가입에서, OAuth는 온보딩에서 확정한다 — 그래서 OAuth 사용자는
     * 프로비저닝~온보딩 사이 정상적으로 {@code null}이다. 단순 NOT NULL 대신 조건부 불변식
     * {@code onboarded ⟹ login_id IS NOT NULL}을 DB CHECK로 보장한다(V15, PR-5).
     */
    @Column(name = "login_id", length = 50)
    private String loginId;

    /**
     * 아이디를 <b>평생 1회</b> 바꿨을 때 넘겨둔 옛 login_id. 아직 안 바꿨으면 {@code null}이다.
     *
     * <p>이 컬럼 하나가 두 몫을 한다: ① 옛 핸들을 <b>영구 예약</b>해(UNIQUE — uk_users_previous_login_id, V69)
     * 제3자가 그 아이디로 갈아타는 사칭을 막고, ② 값이 있다는 사실 자체가 <b>변경권 소진</b> 판정이라
     * 별도 카운터·이력 테이블이 필요 없다({@link #changeLoginId}).
     *
     * <p>옛 아이디로 <b>로그인은 즉시 불가</b>하다({@code BookTimerUserDetailsService}는 login_id만 본다) —
     * 다만 변경 전에 이미 열려 있던 세션은 {@code CurrentUserService}가 이 값으로 브리지해 살려 준다.
     */
    @Column(name = "previous_login_id", length = 50)
    private String previousLoginId;

    /**
     * 토스 앱인토스(미니앱)의 사용자 식별자({@code userKey} — 앱 단위). 이 계정으로 토스 로그인이 가능함을 뜻한다.
     * {@code null}이면 미연결(웹 전용 계정)이라 정상이고, 토스에서 시작했거나 웹 계정을 연결한 계정만 값을 가진다.
     *
     * <p><b>이메일이 아니라 이 값이 미니앱의 신원</b>이다 — 토스 email은 없을 수 있고 소유 보증도 없어,
     * 이메일로 자동 연결하면 계정 탈취 벡터가 된다(설계 §2.2, N-026/N-053과 같은 정신). 그래서 기존 계정과의
     * 연결은 본인이 웹 로그인 상태에서 발급한 일회용 코드로만 이뤄진다({@link #linkTossUserKey}).
     *
     * <p>한 계정에 토스 신원은 하나뿐이고 <b>한 번 붙으면 불변</b>이다(login_id와 같은 once-set 규칙).
     * 한 토스 신원이 두 계정에 붙지 않도록 DB UNIQUE(uk_users_toss_user_key)가 최종 방어선이다.
     */
    @Column(name = "toss_user_key", length = 64)
    private String tossUserKey;

    /**
     * 프로필 아바타로 선택한 도감 작가 캐릭터 코드({@code author_character.code}). <b>파일 업로드 없이</b>
     * 사용자가 도감에서 <b>보유(완독)한 작가</b>의 얼굴(SVG 스프라이트 {@code #sprite-{code}})을 아바타로 재사용한다.
     * {@code null}이면 미선택 — 기존 로그인ID 이니셜 원형으로 폴백한다(opt-in).
     *
     * <p><b>보유 여부 검증은 엔티티가 아니라 서비스</b>({@code ProfileCharacterService})가 한다 — 보유 판정이
     * 완독책·도감 카탈로그에 의존해 엔티티 순수성을 벗어나기 때문(먹이주기 IDOR 방어와 동일 경로). 엔티티는
     * 마케팅 동의 setter처럼 값만 받는다({@link #selectProfileCharacter}/{@link #clearProfileCharacter}).
     */
    @Column(name = "profile_character_code", length = 50)
    private String profileCharacterCode;

    /**
     * 영리목적 광고성 정보(재참여 넛지 등 마케팅 메일) 수신에 동의했는지(이메일 인프라 2단계).
     * 정보통신망법 §50의 <b>사전 동의(opt-in)</b> 불변식 — 기본 {@code false}(미동의)이고, 가입 폼·설정의
     * 별도 선택 항목으로만 켜진다(필수 동의에 끼워팔지 않음 — 개인정보보호법). 미동의자에겐 넛지를 보내지 않는다.
     */
    @Column(name = "marketing_email_consent", nullable = false)
    private boolean marketingEmailConsent = false;

    /**
     * 마지막으로 마케팅 수신에 동의한 시각. 2년 재동의 고지·감사 근거다. 철회({@link #withdrawMarketingConsent})
     * 시에도 보존한다 — "언제 동의했었나"의 이력은 지우지 않는다.
     */
    @Column(name = "marketing_consent_at")
    private java.time.Instant marketingConsentAt;

    /**
     * 마지막 재참여 넛지를 발송한 시각. <b>비활동 구간당 1회</b>를 멱등하게 보장하는 상태다 —
     * {@code lastNudgeSentAt < lastActivityAt}(마지막 활동 이후 아직 안 보냄)일 때만 다음 넛지 대상이 된다.
     * 사용자가 다시 읽으면 활동 시각이 갱신돼 자격이 리셋되고, 또 이탈하면 다음 1회 자격이 생긴다.
     */
    @Column(name = "last_nudge_sent_at")
    private java.time.Instant lastNudgeSentAt;

    /**
     * "오늘 목표 달성" 푸시를 마지막으로 보낸 날(유저 타임존 일자). <b>하루 1회</b>를 멱등하게 보장하는 상태다 —
     * 감지가 분당 폴링이라 이 마킹이 없으면 목표를 넘긴 뒤 매분 같은 알림이 나간다.
     *
     * <p>시각이 아니라 날짜인 이유: 멱등 단위가 유저 타임존의 하루라서다. {@code null}이면 아직 한 번도 안 보냄.
     */
    @Column(name = "goal_met_pushed_on")
    private java.time.LocalDate goalMetPushedOn;

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
     * @param nickname 새 표시 닉네임(공백 불가, {@value #NICKNAME_MAX_LENGTH}자 이내)
     * @param timezone 새 IANA 타임존 ID(예: "Asia/Seoul")
     * @throws IllegalArgumentException 닉네임이 공백·상한 초과이거나 타임존이 유효한 IANA ID가 아닌 경우
     */
    public void updateProfile(String nickname, String timezone) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname must not be blank");
        }
        if (nickname.length() > NICKNAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "nickname must be at most " + NICKNAME_MAX_LENGTH + " chars");
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
     * 프로필 아바타로 쓸 도감 작가 캐릭터를 선택한다. <b>보유(해금) 여부 검증은 호출 전에</b>
     * 서비스({@code ProfileCharacterService})가 끝낸 상태로 들어온다 — 엔티티는 값만 받는다
     * (마케팅 동의 setter와 같은 얇은 책임 분리). {@code null}/공백이면 {@link #clearProfileCharacter()}로
     * 위임해 미선택(이니셜 폴백) 상태로 되돌린다.
     *
     * @param code author_character.code (보유 검증을 통과한 값) — null/공백이면 선택 해제
     */
    public void selectProfileCharacter(String code) {
        if (code == null || code.isBlank()) {
            clearProfileCharacter();
            return;
        }
        this.profileCharacterCode = code;
    }

    /** 프로필 아바타 선택을 해제한다 — 기본(로그인ID 이니셜)으로 되돌린다. 멱등. */
    public void clearProfileCharacter() {
        this.profileCharacterCode = null;
    }

    /** 프로필 아바타로 선택한 도감 작가 캐릭터 코드. 미선택이면 {@code null}(이니셜 폴백). */
    public String getProfileCharacterCode() {
        return profileCharacterCode;
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

    /**
     * 첫 진입 초기 설정(온보딩)을 완료 처리한다. 멱등 — 이미 완료된 계정에 다시 호출해도 무방하다.
     * 실제 타이머 초기값/증가값/상한 적용은 {@code ReadingTimer}가 맡고, 여기선 완료 플래그만 세운다.
     */
    public void completeOnboarding() {
        this.onboarded = true;
    }

    /** 첫 진입 초기 설정(온보딩)을 마쳤는가. false면 온보딩 페이지로 유도된다. */
    public boolean isOnboarded() {
        return onboarded;
    }

    /**
     * 가입 이메일을 검증 완료로 표시한다. 멱등 — 이미 검증된 계정에 다시 호출해도 무방하다(인증 링크 재클릭 등).
     * 호출 경로는 인증 토큰을 소비한 뒤({@code EmailTokenService.consume})의 인증 흐름뿐이다.
     */
    public void verifyEmail() {
        this.emailVerified = true;
    }

    /** 가입 이메일이 검증되었는가. false면 OAuth 자동연결 대상에서 제외(pre-hijacking 차단)되고 인증 배너가 노출된다. */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    /**
     * 마케팅 수신에 동의 처리한다 — 동의 플래그를 켜고 동의 시각을 {@code clock} 기준으로 기록한다(멱등).
     * 시각을 직접 계산하지 않고 {@code Clock}을 주입받는 이유는 테스트에서 동의 경계를 고정하기 위함이다(N-010).
     *
     * @param clock 동의 시각 결정 시계(테스트 고정용)
     */
    public void consentToMarketing(java.time.Clock clock) {
        this.marketingEmailConsent = true;
        this.marketingConsentAt = clock.instant();
    }

    /**
     * 마케팅 수신 동의를 철회한다 — 플래그만 끄고 <b>동의 시각은 감사 근거로 보존</b>한다(언제 동의했었는지의 이력은
     * 지우지 않는다). 설정 토글 OFF·메일 구독해지 링크가 이 경로를 탄다. 멱등.
     */
    public void withdrawMarketingConsent() {
        this.marketingEmailConsent = false;
    }

    /** 영리목적 광고성 정보(마케팅 메일) 수신에 동의했는가. false면 재참여 넛지 대상에서 제외된다. */
    public boolean isMarketingEmailConsent() {
        return marketingEmailConsent;
    }

    /** 마지막으로 마케팅 수신에 동의한 시각(철회해도 보존). 한 번도 동의 안 했으면 {@code null}. */
    public java.time.Instant getMarketingConsentAt() {
        return marketingConsentAt;
    }

    /**
     * 마지막 재참여 넛지를 발송한 시각을 기록한다 — 발송 직후 호출해 이 비활동 구간의 중복 발송을 막는다(멱등 보장).
     *
     * @param when 발송 시각(배치 시계 기준)
     */
    public void recordNudgeSent(java.time.Instant when) {
        this.lastNudgeSentAt = when;
    }

    /** 마지막 재참여 넛지 발송 시각. 한 번도 안 보냈으면 {@code null}. */
    public java.time.Instant getLastNudgeSentAt() {
        return lastNudgeSentAt;
    }

    /**
     * "오늘 목표 달성" 푸시를 보낸 날을 기록한다 — <b>발송 성공 직후에만</b> 호출해 그날 재발송을 막는다(하루 1회 멱등).
     *
     * @param date 발송한 날(유저 타임존 오늘)
     */
    public void markGoalMetPushed(java.time.LocalDate date) {
        this.goalMetPushedOn = date;
    }

    /** 목표 달성 푸시를 마지막으로 보낸 날(유저 TZ). 한 번도 안 보냈으면 {@code null}. */
    public java.time.LocalDate getGoalMetPushedOn() {
        return goalMetPushedOn;
    }

    /** 책BTI "다시 분석"의 하루 허용 횟수(악의적 반복 클릭 → LLM 남용 방어). 무광고(웹) 경로의 천장이다. */
    public static final int DAILY_PERSONALITY_REFRESH_LIMIT = 3;

    /**
     * 광고 경로를 포함한 하루 분석 <b>총량</b> 상한 — 남용 시 최대 손실을 계정당 하루 ≈20원(10회 × LLM ≈2원)으로 유계.
     * 리워드 광고 보상 검증이 클라 신호뿐이라(토스 SDK에 서버사이드 검증 없음) 이 총량이 유일한 실질 방어다.
     *
     * <p>천장은 둘이지만 <b>카운터는 하나</b>다 — 웹 3회는 "총량 10의 앞 3칸"이라, 미니앱 광고 분석이 그날 웹
     * 무광고 칸도 함께 소진한다("하루 분석 총량은 하나"라는 정신 모델. 사용자 확정 2026-08-15).
     * 값 조정은 이 상수 한 곳(운영하며 조정할 값).
     */
    public static final int DAILY_PERSONALITY_TOTAL_LIMIT = 10;

    /**
     * 책BTI "다시 분석" 한도를 한 번 소비하려 시도한다. 허용되면 카운트를 1 올리고 {@code true},
     * 오늘 한도를 이미 다 썼으면 상태를 바꾸지 않고 {@code false}를 반환한다.
     *
     * <p>{@code today}(사용자 타임존 기준 오늘)가 기록된 날짜와 다르면 새 날로 보고 카운트를 0으로 리셋한 뒤
     * 센다 — 자정이 지나면 다시 {@value #DAILY_PERSONALITY_REFRESH_LIMIT}번 가능. "오늘"을 직접 계산하지 않고
     * 인자로 받는 이유는, 타임존·시계 결정을 호출자(서비스/컨트롤러, 주입된 {@code Clock})에 두어 테스트에서
     * 날짜 경계를 고정·검증할 수 있게 하기 위함이다.
     *
     * @param today 사용자 타임존 기준 오늘 날짜
     * @return 소비에 성공(아직 한도 내)했으면 true, 한도 초과면 false(상태 불변)
     */
    public boolean tryConsumePersonalityRefresh(java.time.LocalDate today) {
        return tryConsumePersonalityRefresh(today, DAILY_PERSONALITY_REFRESH_LIMIT);
    }

    /**
     * 같은 카운터에 <b>호출자가 준 천장</b>을 적용해 한 번 소비한다 — 웹(3)·광고 경로(10)가 하루 총량 하나를
     * 공유하는 구조다({@link #DAILY_PERSONALITY_TOTAL_LIMIT}). 자정 리셋 규칙은 위와 같다.
     *
     * @param limit 이 호출 경로의 천장(웹 {@value #DAILY_PERSONALITY_REFRESH_LIMIT} / 광고 {@value #DAILY_PERSONALITY_TOTAL_LIMIT})
     */
    public boolean tryConsumePersonalityRefresh(java.time.LocalDate today, int limit) {
        if (!today.equals(personalityRefreshDate)) {
            personalityRefreshDate = today;
            personalityRefreshCount = 0;
        }
        if (personalityRefreshCount >= limit) {
            return false;
        }
        personalityRefreshCount++;
        return true;
    }

    /**
     * 오늘({@code today}) 기준 남은 "다시 분석" 횟수를 계산한다 — <b>상태를 바꾸지 않는 읽기</b>(화면 표시·버튼 비활성용).
     * 기록된 날짜가 오늘과 다르면(자정 넘김) 아직 안 쓴 것이므로 한도 전부를 반환한다.
     */
    public int remainingPersonalityRefreshes(java.time.LocalDate today) {
        return remainingPersonalityRefreshes(today, DAILY_PERSONALITY_REFRESH_LIMIT);
    }

    /** 호출자가 준 천장 기준 잔여 — 같은 카운터를 보되 채널마다 자기 천장으로 읽는다(읽기 전용). */
    public int remainingPersonalityRefreshes(java.time.LocalDate today, int limit) {
        if (!today.equals(personalityRefreshDate)) {
            return limit;
        }
        return Math.max(0, limit - personalityRefreshCount);
    }

    /**
     * 이 계정을 운영자(ADMIN)로 승격한다. 멱등 — 이미 ADMIN이면 아무 것도 바꾸지 않는다.
     *
     * <p>가입은 항상 {@link Role#USER}로 고정되며(권한 상승 벡터 차단), ADMIN 부여는 이 메서드를
     * 통해서만 일어난다. 호출 경로는 환경변수로 지정한 이메일을 기동 시 승격하는 시드뿐이다
     * (자동 가입 승격 없음).
     *
     * @return 실제로 USER→ADMIN으로 바뀌었으면 {@code true}, 이미 ADMIN이라 변화가 없으면 {@code false}
     */
    public boolean promoteToAdmin() {
        if (this.role == Role.ADMIN) {
            return false;
        }
        this.role = Role.ADMIN;
        return true;
    }

    /**
     * 로그인 아이디(login_id)를 설정/변경한다. 입력을 소문자로 정규화한 뒤 형식·예약어를 검증한다.
     *
     * <p>형식: 영소문자/숫자/언더스코어 3~20자(대문자는 소문자로 정규화 — 대소문자 구분 안 함). 예약어
     * (admin·root 등)는 거부한다. <b>유니크 보장은 이 메서드의 책임이 아니다</b> — DB 유니크 제약 +
     * 서비스의 사전 중복 확인(PR-2)이 담당한다(여기선 형식만).
     *
     * <p><b>한번 정해지면 영원히 불변</b>이다(공개 @핸들 = 영구 식별자, 인스타·X 모델). 이미 설정된 뒤
     * 재호출하면(같은 값이든 다른 값이든) {@link IllegalStateException}을 던진다. 표시 이름은 nickname이
     * 담당하며 자유롭게 바꿀 수 있다 — login_id만 불변이다.
     *
     * @param rawLoginId 사용자가 입력한 아이디(정규화 전)
     * @throws IllegalStateException    이미 login_id가 설정돼 있는 경우(불변 위반)
     * @throws IllegalArgumentException 공백/형식 불일치/예약어인 경우
     */
    public void assignLoginId(String rawLoginId) {
        if (this.loginId != null) {
            throw new IllegalStateException(
                    "login_id is immutable and already set: " + this.loginId);
        }
        this.loginId = normalizeLoginId(rawLoginId);
    }

    /**
     * 아이디를 <b>평생 1회</b> 교체한다 — 옛 아이디는 {@link #previousLoginId}로 옮겨 영구히 잠근다.
     *
     * <p>{@link #assignLoginId}(once-set: 없는 값을 처음 정한다)와 별개 규칙이다: 이쪽은 <b>이미 있는 값을
     * 1회만 갈아끼운다</b>. 옛 아이디를 지우지 않고 보관하는 이유는 남이 그 핸들로 갈아타는 사칭을 막기
     * 위해서다(유니크는 여기 책임이 아니라 서비스의 사전 확인 + DB UNIQUE 분업 — {@code assignLoginId}와 동일).
     *
     * <p>되돌리기 특례는 없다 — 자기 옛 아이디로 돌아가는 것도 소진 검사에 먼저 걸린다. 특례를 열면
     * A↔B 왕복(핸들 세탁)이 가능해져 1회 제한의 목적이 무너진다.
     *
     * @param rawNewLoginId 사용자가 입력한 새 아이디(정규화 전)
     * @throws IllegalStateException    login_id가 아직 없거나(온보딩 전) 변경권을 이미 쓴 경우
     * @throws IllegalArgumentException 공백/형식 불일치/예약어이거나, 현재 아이디와 같은 경우
     */
    public void changeLoginId(String rawNewLoginId) {
        if (this.loginId == null) {
            throw new IllegalStateException("cannot change login_id before it is set");
        }
        if (this.previousLoginId != null) {
            throw new IllegalStateException("login_id change already used: " + this.previousLoginId);
        }
        String normalized = normalizeLoginId(rawNewLoginId);
        if (normalized.equals(this.loginId)) {
            throw new IllegalArgumentException("new login_id equals current: " + normalized);
        }
        this.previousLoginId = this.loginId;
        this.loginId = normalized;
    }

    /** 아이디를 1회 바꿨다면 그 <b>옛 login_id</b>, 아직 안 바꿨으면 {@code null}(= 변경권 미사용). */
    public String getPreviousLoginId() {
        return previousLoginId;
    }

    /**
     * 입력 아이디를 정규화(앞뒤 공백 제거 + 소문자)하고 형식·예약어를 검증해 반환한다 — <b>상태를 바꾸지 않는다</b>.
     *
     * <p>서비스가 유니크 사전 확인을 할 때 쓰는 정규화 단일 출처다: {@code assignLoginId} 전에 이 메서드로
     * 정규화한 값으로 {@code existsByLoginId}를 조회하면, 아직 영속화되지 않은 자기 자신과 오탐 매칭되는
     * 것을 피하면서 형식·예약어 검증을 한 곳에서 공유한다(불변 검사만 {@code assignLoginId}가 추가로 한다).
     *
     * @param rawLoginId 사용자가 입력한 아이디(정규화 전)
     * @return 정규화된 login_id(소문자)
     * @throws IllegalArgumentException 공백/형식 불일치/예약어인 경우
     */
    public static String normalizeLoginId(String rawLoginId) {
        if (rawLoginId == null || rawLoginId.isBlank()) {
            throw new IllegalArgumentException("login_id must not be blank");
        }
        String normalized = rawLoginId.strip().toLowerCase(Locale.ROOT);
        if (!LOGIN_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "login_id must be 3-20 chars of [a-z0-9_]: " + rawLoginId);
        }
        if (RESERVED_LOGIN_IDS.contains(normalized)) {
            throw new IllegalArgumentException("login_id is reserved: " + normalized);
        }
        return normalized;
    }

    /** 로그인/식별용 아이디이자 공개 @핸들(검색·프로필 URL). 아직 설정 전이면 {@code null}. */
    public String getLoginId() {
        return loginId;
    }

    /**
     * 이 계정에 토스 신원({@code userKey})을 연결한다 — 이후 미니앱의 토스 로그인이 이 계정으로 들어온다.
     *
     * <p><b>한 번 연결되면 불변</b>이다({@link #assignLoginId}와 같은 once-set 규칙): 한 계정에 토스 신원은
     * 하나뿐이고, 나중에 다른 신원으로 갈아끼우는 것은 계정 이관에 가까워 도메인이 허용하지 않는다. 이미
     * 연결된 계정에 다시 호출하면(같은 값이든 다른 값이든) {@link IllegalStateException}을 던진다 —
     * 서비스는 이걸 409로 매핑한다. 연결 해제는 v2(문의 처리)다.
     *
     * <p><b>유니크는 이 메서드의 책임이 아니다</b> — 다른 계정이 이미 그 userKey를 쓰는지는 서비스의 사전
     * 확인과 DB UNIQUE 제약이 담당한다(login_id 유니크 처리와 동일한 분업).
     *
     * @param userKey 토스가 준 앱 단위 사용자 식별자(공백 불가)
     * @throws IllegalStateException    이미 토스 신원이 연결된 경우(불변 위반)
     * @throws IllegalArgumentException userKey가 null/공백인 경우
     */
    public void linkTossUserKey(String userKey) {
        if (this.tossUserKey != null) {
            throw new IllegalStateException(
                    "toss_user_key is immutable and already set: " + this.tossUserKey);
        }
        if (userKey == null || userKey.isBlank()) {
            throw new IllegalArgumentException("toss user key must not be blank");
        }
        this.tossUserKey = userKey.strip();
    }

    /** 연결된 토스 사용자 식별자(userKey). 미연결(웹 전용 계정)이면 {@code null}. */
    public String getTossUserKey() {
        return tossUserKey;
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
