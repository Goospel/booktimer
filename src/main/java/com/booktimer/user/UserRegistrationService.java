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
     * @param rawPassword 평문 비밀번호(여기서 해싱 — 평문은 저장되지 않는다)
     * @param startDate   누적 시작 기준일(유저 타임존 기준 오늘)
     * @return 저장된 User
     */
    public User register(String email, String rawPassword, String nickname,
                         String timezone, Role role, LocalDate startDate) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = userRepository.save(User.of(email, passwordHash, nickname, timezone, role));
        timerRepository.save(ReadingTimer.startFor(
                user, DEFAULT_DAILY_INCREMENT_SECONDS, DEFAULT_CAP_SECONDS, startDate));
        return user;
    }
}
