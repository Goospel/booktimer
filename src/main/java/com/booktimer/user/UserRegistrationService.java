package com.booktimer.user;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
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
 * <p>비밀번호 해싱과 "오늘"(유저 타임존) 계산은 이 서비스의 책임이 아니다 — 이미 해시된
 * 비밀번호와 누적 시작일({@code startDate})을 받는다(상위 계층이 결정).
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

    public UserRegistrationService(UserRepository userRepository,
                                   ReadingTimerRepository timerRepository) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
    }

    /**
     * 새 사용자를 등록하고 기본 설정의 누적 타이머를 함께 만든다.
     *
     * @param passwordHash 이미 해시된 비밀번호(평문 금지)
     * @param startDate    누적 시작 기준일(유저 타임존 기준 오늘)
     * @return 저장된 User
     */
    public User register(String email, String passwordHash, String nickname,
                         String timezone, Role role, LocalDate startDate) {
        User user = userRepository.save(User.of(email, passwordHash, nickname, timezone, role));
        timerRepository.save(ReadingTimer.startFor(
                user, DEFAULT_DAILY_INCREMENT_SECONDS, DEFAULT_CAP_SECONDS, startDate));
        return user;
    }
}
