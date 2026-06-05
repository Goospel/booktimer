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
     * 온보딩을 완료한다 — (필요 시) login_id를 확정하고, 닉네임을 확정하고, 타이머에 초기값/증가값/상한을
     * 적용하고 사용자를 온보딩 완료로 표시한다.
     *
     * <p><b>login_id는 아직 없을 때만(=소셜 로그인 사용자) 여기서 확정</b>한다. 로컬 가입자는 가입에서 이미
     * login_id를 받았으므로(PR-4) 불변 규칙상 다시 정하지 않고 건너뛴다 — {@code loginId} 인자는 무시된다.
     * 닉네임은 단순 표시 이름이라 중복을 허용한다.
     *
     * @param user                    대상 사용자(인증된 principal에서 해석된 엔티티)
     * @param loginId                 login_id가 아직 없을 때 사용자가 정한 공개 핸들(불변 — 형식·예약어·유니크 검증)
     * @param nickname                사용자가 확정한 표시 닉네임(중복 허용)
     * @param initialRemainingSeconds 사용자가 정한 초기 잔여(초, cap 초과 시 클램프)
     * @param dailyIncrementSeconds   하루 증가값(초)
     * @param capSeconds              누적 상한(초)
     * @param today                   온보딩 시점의 "오늘"(유저 타임존 기준 — 누적 기준일로 리셋)
     * @throws IllegalStateException          사용자/타이머가 없는 경우
     * @throws IllegalArgumentException       값 검증 실패 시(도메인 위임 — login_id 형식/예약어 포함)
     * @throws LoginIdAlreadyExistsException  login_id가 이미 쓰이는 경우
     */
    public void complete(User user, String loginId, String nickname, long initialRemainingSeconds,
                         long dailyIncrementSeconds, long capSeconds, LocalDate today) {
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user: " + user.getEmail()));

        // login_id가 아직 없을 때(소셜 로그인)만 온보딩에서 확정한다. 로컬은 가입에서 받았으므로 건너뛴다(불변).
        if (user.getLoginId() == null) {
            // 정규화·형식·예약어는 도메인이 검증(IAE), 유니크는 정규화값으로 사전 확인한다 — assign 전에
            // 확인해야 (아직 미영속인) 자기 자신과의 오탐을 피한다.
            String normalizedLoginId = User.normalizeLoginId(loginId);
            if (userRepository.existsByLoginId(normalizedLoginId)) {
                throw new LoginIdAlreadyExistsException(normalizedLoginId);
            }
            user.assignLoginId(loginId);
        }

        user.updateProfile(nickname, user.getTimezone());
        timer.applyInitialSetup(initialRemainingSeconds, dailyIncrementSeconds, capSeconds, today);
        user.completeOnboarding();

        timerRepository.save(timer);
        userRepository.save(user);
    }
}
