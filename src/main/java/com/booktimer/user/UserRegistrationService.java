package com.booktimer.user;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 신규 가입 오케스트레이션.
 *
 * <p>User 생성과 동시에 ReadingTimer를 기본 설정으로 부트스트랩한다 — 둘은 한 트랜잭션에서
 * 함께 만들어져야 한다. 이렇게 해야 "모든 User는 타이머를 가진다"가 불변식이 되고,
 * 세션 stop 시 타이머가 없어 실패하는 일이 없다.
 *
 * <p>비밀번호는 <b>평문으로 받아 {@link PasswordEncoder}로 해싱</b>한 뒤 저장한다 — 평문은
 * 영속화되지 않는다. 단, "오늘"(유저 타임존) 계산은 여전히 이 서비스의 책임이 아니다 —
 * 누적 시작일({@code startDate})은 상위 계층(시계+유저 TZ)이 결정해 넘긴다.
 */
@Service
@Transactional
public class UserRegistrationService {

    /** 기본 하루 증가값: 1시간. */
    public static final long DEFAULT_DAILY_INCREMENT_SECONDS = 3600L;

    /** 기본 누적 상한: 5시간. */
    public static final long DEFAULT_CAP_SECONDS = 18_000L;

    private final UserRepository userRepository;
    private final ReadingTimerRepository timerRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserRepository userRepository,
                                   ReadingTimerRepository timerRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 새 사용자를 등록하고 기본 설정의 누적 타이머를 함께 만든다.
     *
     * <p><b>로컬 가입은 login_id(로그인 식별자·공개 @핸들)를 여기서 확정</b>한다(PR-4 인증 컷오버) — 로그인이
     * login_id 기준이라 가입 시점에 있어야 첫 로그인이 가능하다. 형식/예약어는 도메인이, 유니크는 사전 확인 +
     * DB 제약이 담당한다. (소셜 가입은 비밀번호가 없어 {@link #registerOAuth}로 가고, login_id는 온보딩에서 정한다.)
     *
     * @param rawPassword 평문 비밀번호(여기서 해싱 — 평문은 저장되지 않는다)
     * @param loginId     사용자가 정한 로그인 아이디(불변 — 정규화·형식·예약어·유니크 검증)
     * @param startDate   누적 시작 기준일(유저 타임존 기준 오늘)
     * @return 저장된 User
     * @throws EmailAlreadyExistsException    이메일이 이미 쓰이는 경우
     * @throws LoginIdAlreadyExistsException  login_id가 이미 쓰이는 경우
     * @throws IllegalArgumentException       login_id 형식/예약어 위반(도메인 검증)
     */
    public User register(String email, String rawPassword, String loginId, String nickname,
                         String timezone, Role role, LocalDate startDate) {
        // 검사 순서: login_id(형식→유니크) 먼저, email은 마지막. login_id는 공개 @핸들이라 "사용 중" 노출이
        // 무해하고 UX상 필요(다른 아이디를 골라야 함)하지만, email은 비공개 속성이라 "이미 가입됨" 노출이
        // 곧 계정 열거가 된다. 그래서 email 중복은 가장 마지막에 던지고, 컨트롤러가 이를 가입 성공과 동일한
        // 응답으로 흡수한다(존재 여부 미노출). login_id 충돌은 그 전에 잡혀 정상적으로 필드 에러로 안내된다.
        String normalizedLoginId = User.normalizeLoginId(loginId);
        if (userRepository.existsByLoginId(normalizedLoginId)) {
            throw new LoginIdAlreadyExistsException(normalizedLoginId);
        }
        if (userRepository.existsByEmail(email)) {
            // DB 유니크 제약(uk_users_email) 위반이 500으로 새기 전에 미리 막는다. 컨트롤러는 열거 완화를 위해
            // 이 예외를 가입 성공과 동일한 응답으로 흡수한다(이메일 존재 여부를 응답으로 구분할 수 없게).
            throw new EmailAlreadyExistsException(email);
        }
        // nickname은 더 이상 유니크가 아니다(단순 표시 이름) — 중복 확인 없이 그대로 저장한다.
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = User.of(email, passwordHash, nickname, timezone, role);
        user.assignLoginId(loginId); // 불변 — 가입에서 단 한 번 확정
        return persistWithTimer(user, startDate);
    }

    /**
     * login_id를 <b>미설정(null)</b>으로 로컬 사용자를 만든다 — login_id를 나중에(예: 온보딩) 확정하는 경로 또는
     * 테스트 편의용. <b>프로덕션 로컬 가입은 login_id를 받는 위 오버로드를 쓴다</b>(로그인 식별자가 login_id라
     * 가입 시 있어야 첫 로그인이 된다). 이 경로로 만든 사용자는 login_id가 채워지기 전엔 로그인할 수 없다.
     */
    public User register(String email, String rawPassword, String nickname,
                         String timezone, Role role, LocalDate startDate) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        return persistWithTimer(User.of(email, passwordHash, nickname, timezone, role), startDate);
    }

    private User persistWithTimer(User user, LocalDate startDate) {
        user = userRepository.save(user);
        timerRepository.save(ReadingTimer.startFor(
                user, DEFAULT_DAILY_INCREMENT_SECONDS, DEFAULT_CAP_SECONDS, startDate));
        return user;
    }

    /**
     * 소셜 로그인(OAuth)으로 들어온 사용자를 비밀번호 없이 등록하고, 기본 설정의 타이머를 함께 만든다.
     * provider가 신원을 보증하므로 평문 비밀번호·해싱이 없다는 점만 {@link #register}와 다르다.
     *
     * @param provider  소셜 provider(GOOGLE 등 — LOCAL 불가)
     * @param startDate 누적 시작 기준일(유저 타임존 기준 오늘)
     * @return 저장된 User
     */
    public User registerOAuth(String email, String nickname, String timezone,
                              AuthProvider provider, LocalDate startDate) {
        User user = userRepository.save(User.ofOAuth(email, nickname, timezone, Role.USER, provider));
        timerRepository.save(ReadingTimer.startFor(
                user, DEFAULT_DAILY_INCREMENT_SECONDS, DEFAULT_CAP_SECONDS, startDate));
        return user;
    }
}
