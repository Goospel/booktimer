package com.booktimer.user;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설정 변경 오케스트레이션.
 *
 * <p>사용자가 바꾸는 설정은 두 엔티티에 걸쳐 있다 — 프로필(User: 닉네임/타임존)과
 * 타이머 설정(ReadingTimer: 증가값/cap). 이 둘을 <b>한 트랜잭션</b>에서 함께 갱신해
 * 부분 적용(닉네임만 바뀌고 cap은 안 바뀜 등)을 막는다.
 *
 * <p>검증·클램프 같은 규칙은 도메인 메서드({@link User#updateProfile},
 * {@link ReadingTimer#updateSettings})가 책임진다 — 서비스는 조회·위임·저장만 한다.
 * 식별자(email)로 트랜잭션 안에서 직접 로드해 영속 엔티티를 변경(dirty checking)한다.
 */
@Service
@Transactional
public class UserSettingsService {

    private final UserRepository userRepository;
    private final ReadingTimerRepository timerRepository;

    public UserSettingsService(UserRepository userRepository,
                               ReadingTimerRepository timerRepository) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
    }

    /**
     * 사용자의 프로필(닉네임/타임존)과 타이머 설정(증가값/cap)을 갱신한다.
     *
     * @param email                 대상 사용자 식별자
     * @param nickname              새 닉네임
     * @param timezone              새 IANA 타임존 ID
     * @param dailyIncrementSeconds 새 하루 증가값(초)
     * @param capSeconds            새 누적 상한(초)
     * @throws IllegalStateException    사용자/타이머가 없는 경우
     * @throws IllegalArgumentException 값 검증 실패 시(도메인 위임)
     */
    public void updateSettings(String email, String nickname, String timezone,
                               long dailyIncrementSeconds, long capSeconds) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user: " + email));

        // nickname은 단순 표시 이름 — 중복을 허용하고 자유롭게 바꾼다(영구 식별자는 불변의 login_id).
        user.updateProfile(nickname, timezone);
        timer.updateSettings(dailyIncrementSeconds, capSeconds);

        userRepository.save(user);
        timerRepository.save(timer);
    }
}
