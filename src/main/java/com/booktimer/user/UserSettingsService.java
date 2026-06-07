package com.booktimer.user;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설정 변경 오케스트레이션.
 *
 * <p>사용자가 바꾸는 설정은 두 엔티티에 걸쳐 있다 — 프로필(User: 닉네임/타임존)과
 * 타이머 설정(ReadingTimer: 하루 목표). 이 둘을 <b>한 트랜잭션</b>에서 함께 갱신해
 * 부분 적용(닉네임만 바뀌고 목표는 안 바뀜 등)을 막는다.
 *
 * <p>검증 같은 규칙은 도메인 메서드({@link User#updateProfile},
 * {@link ReadingTimer#updateSettings})가 책임진다 — 서비스는 조회·위임·저장만 한다.
 * 식별자(email)로 트랜잭션 안에서 직접 로드해 영속 엔티티를 변경(dirty checking)한다.
 *
 * <p>옛 "누적 상한(cap)"은 7일 윈도우 부채 모델로 전환하며 설정에서 빠졌다 — 잔재 컬럼
 * (remaining/cap/last_accrual)도 제거됐다(PR #218). 설정은 이제 하루 목표만 바꾼다.
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
     * 사용자의 프로필(닉네임/타임존)과 타이머 하루 목표를 갱신한다.
     *
     * @param email                 대상 사용자 식별자
     * @param nickname              새 닉네임
     * @param timezone              새 IANA 타임존 ID
     * @param dailyIncrementSeconds 새 하루 목표(초)
     * @throws IllegalStateException    사용자/타이머가 없는 경우
     * @throws IllegalArgumentException 값 검증 실패 시(도메인 위임)
     */
    public void updateSettings(String email, String nickname, String timezone,
                               long dailyIncrementSeconds) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user: " + email));

        // nickname은 단순 표시 이름 — 중복을 허용하고 자유롭게 바꾼다(영구 식별자는 불변의 login_id).
        user.updateProfile(nickname, timezone);
        timer.updateSettings(dailyIncrementSeconds); // 하루 목표만 — cap은 7일 윈도우 모델로 대체됨

        userRepository.save(user);
        timerRepository.save(timer);
    }
}
