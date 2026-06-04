package com.booktimer.user;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 첫 진입 초기 설정(온보딩) 오케스트레이션.
 *
 * <p>온보딩은 두 엔티티에 걸쳐 있다 — 타이머 초기값/증가값/상한({@link ReadingTimer})과
 * 완료 플래그({@link User}). 이 둘을 <b>한 트랜잭션</b>에서 함께 갱신해 부분 적용
 * (타이머만 바뀌고 완료 표시는 안 됨 등)을 막는다.
 *
 * <p>검증·클램프 같은 규칙은 도메인 메서드({@link ReadingTimer#applyInitialSetup},
 * {@link User#completeOnboarding})가 책임진다 — 서비스는 조회·위임·저장만 한다.
 * "오늘"(유저 타임존)은 상위 계층(시계+유저 TZ)이 계산해 넘긴다(N-010).
 */
@Service
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;
    private final ReadingTimerRepository timerRepository;

    public OnboardingService(UserRepository userRepository,
                             ReadingTimerRepository timerRepository) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
    }

    /**
     * 온보딩을 완료한다 — 닉네임을 확정하고, 타이머에 초기값/증가값/상한을 적용하고 사용자를 온보딩 완료로 표시한다.
     *
     * <p>소셜 로그인 사용자는 가입 시 닉네임이 자동 배정되므로(중복 회피 접미사 포함), 여기서 직접 원하는
     * 닉네임을 고른다. 닉네임은 유니크(uk_users_nickname)라, 남이 쓰는 값으로 바꾸려 하면 거부한다 —
     * 단 자기 현재(자동 배정된) 닉네임을 그대로 두는 것은 충돌이 아니다.
     *
     * @param email                   대상 사용자 식별자
     * @param nickname                사용자가 확정한 닉네임(유니크)
     * @param initialRemainingSeconds 사용자가 정한 초기 잔여(초, cap 초과 시 클램프)
     * @param dailyIncrementSeconds   하루 증가값(초)
     * @param capSeconds              누적 상한(초)
     * @param today                   온보딩 시점의 "오늘"(유저 타임존 기준 — 누적 기준일로 리셋)
     * @throws IllegalStateException          사용자/타이머가 없는 경우
     * @throws IllegalArgumentException       값 검증 실패 시(도메인 위임)
     * @throws NicknameAlreadyExistsException 닉네임이 이미 쓰이는 경우(자기 현재 닉 유지는 예외)
     */
    public void complete(String email, String nickname, long initialRemainingSeconds,
                         long dailyIncrementSeconds, long capSeconds, LocalDate today) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user: " + email));

        // 닉네임을 남이 쓰는 값으로 바꾸려 하면 거부(uk_users_nickname). 자기 현재 닉 유지는 충돌 아님.
        if (!user.getNickname().equals(nickname) && userRepository.existsByNickname(nickname)) {
            throw new NicknameAlreadyExistsException(nickname);
        }

        user.updateProfile(nickname, user.getTimezone());
        timer.applyInitialSetup(initialRemainingSeconds, dailyIncrementSeconds, capSeconds, today);
        user.completeOnboarding();

        timerRepository.save(timer);
        userRepository.save(user);
    }
}
