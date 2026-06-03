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
     * 온보딩을 완료한다 — 타이머에 초기값/증가값/상한을 적용하고 사용자를 온보딩 완료로 표시한다.
     *
     * @param email                   대상 사용자 식별자
     * @param initialRemainingSeconds 사용자가 정한 초기 잔여(초, cap 초과 시 클램프)
     * @param dailyIncrementSeconds   하루 증가값(초)
     * @param capSeconds              누적 상한(초)
     * @param today                   온보딩 시점의 "오늘"(유저 타임존 기준 — 누적 기준일로 리셋)
     * @throws IllegalStateException    사용자/타이머가 없는 경우
     * @throws IllegalArgumentException 값 검증 실패 시(도메인 위임)
     */
    public void complete(String email, long initialRemainingSeconds,
                         long dailyIncrementSeconds, long capSeconds, LocalDate today) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user: " + email));

        timer.applyInitialSetup(initialRemainingSeconds, dailyIncrementSeconds, capSeconds, today);
        user.completeOnboarding();

        timerRepository.save(timer);
        userRepository.save(user);
    }
}
