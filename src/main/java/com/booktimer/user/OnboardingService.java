package com.booktimer.user;

import com.booktimer.timer.ReadingGoalService;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 진입 초기 설정(온보딩) 오케스트레이션.
 *
 * <p>온보딩은 두 엔티티에 걸쳐 있다 — 타이머 하루 목표({@link ReadingTimer})와
 * 완료 플래그({@link User}). 이 둘을 <b>한 트랜잭션</b>에서 함께 갱신해 부분 적용
 * (타이머만 바뀌고 완료 표시는 안 됨 등)을 막는다.
 *
 * <p>검증 같은 규칙은 도메인 메서드({@link User#completeOnboarding})가 책임진다 — 서비스는
 * 조회·위임·저장만 한다.
 *
 * <p>옛 "초기 잔여"·"누적 상한"은 7일 윈도우 부채 모델로 전환하며 사라졌다 — 온보딩은 이제 하루
 * 목표만 정한다. 잔재 컬럼(remaining/cap/last_accrual)도 제거됐다(PR #218).
 */
@Service
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;
    private final ReadingTimerRepository timerRepository;
    private final ReadingGoalService goalService;

    public OnboardingService(UserRepository userRepository,
                             ReadingTimerRepository timerRepository,
                             ReadingGoalService goalService) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
        this.goalService = goalService;
    }

    /**
     * 온보딩을 완료한다 — (필요 시) login_id를 확정하고, 닉네임을 확정하고, 타이머 하루 목표를 적용하고
     * 사용자를 온보딩 완료로 표시한다.
     *
     * <p><b>login_id는 아직 없을 때만(=소셜 로그인 사용자) 여기서 확정</b>한다. 로컬 가입자는 가입에서 이미
     * login_id를 받았으므로(PR-4) 불변 규칙상 다시 정하지 않고 건너뛴다 — {@code loginId} 인자는 무시된다.
     * 닉네임은 단순 표시 이름이라 중복을 허용한다.
     *
     * @param user                  대상 사용자(인증된 principal에서 해석된 엔티티)
     * @param loginId               login_id가 아직 없을 때 사용자가 정한 공개 핸들(불변 — 형식·예약어·유니크 검증)
     * @param nickname              사용자가 확정한 표시 닉네임(중복 허용)
     * @param dailyIncrementSeconds 하루 목표(초)
     * @throws IllegalStateException          사용자/타이머가 없는 경우
     * @throws IllegalArgumentException       값 검증 실패 시(도메인 위임 — login_id 형식/예약어 포함)
     * @throws LoginIdAlreadyExistsException  login_id가 이미 쓰이는 경우
     */
    public void complete(User user, String loginId, String nickname, long dailyIncrementSeconds) {
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
        timer.updateSettings(dailyIncrementSeconds); // 하루 목표만 — cap은 7일 윈도우 모델로 대체됨
        goalService.record(user, dailyIncrementSeconds); // 오늘자 baseline 목표 이력 (이후 변경의 비교 기준 + 과거 판정)
        user.completeOnboarding();

        timerRepository.save(timer);
        userRepository.save(user);
    }

    /**
     * 하루 목표만 정한다 — 미니앱(앱인토스) 경량 온보딩 (설계 §2.4).
     *
     * <p>{@link #complete}에서 <b>login_id 확정과 {@code completeOnboarding()}을 뺀 것</b>이다. 미니앱 계정은
     * 핸들이 없으므로 여기서 온보딩 완료로 표시하면 DB CHECK({@code onboarded ⟹ login_id IS NOT NULL})를
     * 위반한다 — 그래서 이 경로는 온보딩 플래그를 <b>건드리지 않는다</b>. 핸들 만들기는 나중에 사용자가 스스로
     * {@link #assignHandle}로 한다({@code POST /api/miniapp/handle}).
     *
     * @throws IllegalStateException    타이머가 없는 경우
     * @throws IllegalArgumentException 목표가 음수인 경우(도메인 위임)
     */
    public void setDailyGoal(User user, long dailyIncrementSeconds) {
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user: " + user.getEmail()));

        timer.updateSettings(dailyIncrementSeconds);
        goalService.record(user, dailyIncrementSeconds); // 그날 목표로 과거를 판정하기 위한 시점 기록
        timerRepository.save(timer);
    }

    /**
     * 미니앱 핸들 설정 — login_id(공개 @핸들)를 확정하고 온보딩 완료로 표시한다.
     *
     * <p>{@link #complete}에서 <b>닉네임·하루 목표를 뺀 것</b>이다 — 목표는 미니앱이 이미
     * {@link #setDailyGoal}로 정했으므로 여기서 다시 기록하면 목표 이력이 중복된다. 핸들이 없으면
     * 소셜 전 경로의 노출 필터({@code login_id is not null})에 걸려 영영 발견되지 않으므로, 이 메서드가
     * 토스로 시작한 계정의 유일한 탈출구다.
     *
     * <p>{@code completeOnboarding()}을 함께 부르는 것은 안전하다 — CHECK 불변식이
     * {@code onboarded ⟹ login_id IS NOT NULL} 방향이라 핸들을 먼저 확정한 뒤이기 때문이다.
     *
     * @param user        대상 사용자(인증된 principal에서 해석된 엔티티)
     * @param rawLoginId  사용자가 정한 공개 핸들(정규화 전 — 형식·예약어·유니크 검증)
     * @throws IllegalStateException         이미 핸들이 있는 경우(once-set 불변)
     * @throws IllegalArgumentException      형식·예약어 위반(도메인 위임)
     * @throws LoginIdAlreadyExistsException 남이 이미 쓰는 핸들인 경우
     */
    public void assignHandle(User user, String rawLoginId) {
        // 정규화·중복조회 전에 끊는다 — already-set을 도메인 예외가 아닌 명시 경로로 409에 매핑하기 위함
        // (complete()의 `if (user.getLoginId() == null)` 분기와 대칭).
        if (user.getLoginId() != null) {
            throw new IllegalStateException("login_id already set: " + user.getLoginId());
        }
        // 유니크는 정규화값으로 사전 확인한다 — assign 전에 봐야 (아직 미영속인) 자기 자신과 오탐하지 않는다.
        String normalized = User.normalizeLoginId(rawLoginId);
        if (userRepository.existsByLoginId(normalized)) {
            throw new LoginIdAlreadyExistsException(normalized);
        }
        user.assignLoginId(rawLoginId);
        user.completeOnboarding();
        userRepository.save(user);
    }
}
