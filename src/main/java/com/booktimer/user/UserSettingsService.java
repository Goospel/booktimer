package com.booktimer.user;

import com.booktimer.timer.ReadingGoalService;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
    private final ReadingGoalService goalService;
    private final Clock clock;

    public UserSettingsService(UserRepository userRepository,
                               ReadingTimerRepository timerRepository,
                               ReadingGoalService goalService,
                               Clock clock) {
        this.userRepository = userRepository;
        this.timerRepository = timerRepository;
        this.goalService = goalService;
        this.clock = clock;
    }

    /**
     * 사용자의 프로필(닉네임/타임존)과 타이머 하루 목표를 갱신한다.
     *
     * @param email                 대상 사용자 식별자
     * @param nickname              새 닉네임
     * @param timezone              새 IANA 타임존 ID
     * @param dailyIncrementSeconds 새 하루 목표(초)
     * @param debtCarryover         밀린 부채를 헤드라인에 합산 표시할지(표시 전용 토글)
     * @throws IllegalStateException    사용자/타이머가 없는 경우
     * @throws IllegalArgumentException 값 검증 실패 시(도메인 위임)
     */
    public void updateSettings(String email, String nickname, String timezone,
                               long dailyIncrementSeconds, boolean debtCarryover) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user: " + email));

        // nickname은 단순 표시 이름 — 중복을 허용하고 자유롭게 바꾼다(영구 식별자는 불변의 login_id).
        user.updateProfile(nickname, timezone);

        // 하루 목표가 실제로 바뀔 때만 오늘자 변경 이력을 남긴다 — 닉네임/토글만 바꿔도 행이 쌓이지 않게.
        boolean goalChanged = timer.getDailyIncrementSeconds() != dailyIncrementSeconds;
        timer.updateSettings(dailyIncrementSeconds, debtCarryover); // 하루 목표 + 부채 합산 표시 토글
        if (goalChanged) {
            goalService.record(user, dailyIncrementSeconds); // 그날 목표로 과거를 판정하기 위한 시점 기록
        }

        userRepository.save(user);
        timerRepository.save(timer);
    }

    /**
     * 닉네임만 갱신한다 — 미니앱 설정 경로. 웹 설정은 타임존·하루 목표와 한 트랜잭션인
     * {@link #updateSettings}를 타지만, 미니앱은 타임존을 노출하지 않아(한국 전용 채널) 바꿀 값이 이것뿐이다.
     *
     * <p>검증은 도메인({@link User#updateProfile})이 단일 출처다 — 타임존은 현재 값을 그대로 재전달해
     * "닉네임만 바뀐다"를 만든다.
     *
     * @param user     대상 사용자(이미 해석된 영속 엔티티)
     * @param nickname 새 닉네임
     * @throws IllegalArgumentException 공백이거나 {@link User#NICKNAME_MAX_LENGTH}자를 넘는 경우(도메인 위임)
     */
    public void updateNickname(User user, String nickname) {
        user.updateProfile(nickname, user.getTimezone());
        userRepository.save(user);
    }

    /**
     * 마케팅(재참여 넛지) 수신 동의를 켜거나 끈다 — 설정의 "소식·알림" 토글이 타는 경로(철회 보장).
     * 동의면 {@link User#consentToMarketing(Clock)}로 동의 시각을 기록하고, 미동의면
     * {@link User#withdrawMarketingConsent()}로 끈다(동의 시각은 감사용 보존). 동의 시각은 주입된 {@link Clock}이
     * 결정한다(N-010 — 테스트 고정).
     *
     * @param email   대상 사용자 식별자
     * @param consent 마케팅 수신 동의 여부(true=동의, false=철회)
     * @throws IllegalStateException 사용자가 없는 경우
     */
    public void updateMarketingConsent(String email, boolean consent) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        if (consent) {
            user.consentToMarketing(clock);
        } else {
            user.withdrawMarketingConsent();
        }
        userRepository.save(user);
    }
}
