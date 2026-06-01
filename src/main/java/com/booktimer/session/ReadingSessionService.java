package com.booktimer.session;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 독서 세션 start/stop 유스케이스 오케스트레이션.
 *
 * <p>도메인 규칙은 엔티티({@link ReadingSession}, {@link ReadingTimer})에 있고, 이 서비스는
 * 레포지토리 조회·저장과 두 엔티티의 협력을 트랜잭션 경계 안에서 엮는다:
 * <ul>
 *   <li>start — 진행 중 세션이 있으면 거부, 없으면 새 세션 저장.</li>
 *   <li>stop — 진행 중 세션을 종료하고 측정량을 유저 타이머에서 차감, 둘 다 저장.</li>
 * </ul>
 */
@Service
@Transactional
public class ReadingSessionService {

    private final ReadingSessionRepository sessionRepository;
    private final ReadingTimerRepository timerRepository;

    public ReadingSessionService(ReadingSessionRepository sessionRepository,
                                 ReadingTimerRepository timerRepository) {
        this.sessionRepository = sessionRepository;
        this.timerRepository = timerRepository;
    }

    /**
     * 새 측정 세션을 시작한다.
     *
     * @throws IllegalStateException 이미 진행 중인 세션이 있는 경우
     */
    public ReadingSession start(User user, Instant now) {
        sessionRepository.findByUserAndEndedAtIsNull(user).ifPresent(s -> {
            throw new IllegalStateException("an active session already exists");
        });
        return sessionRepository.save(ReadingSession.start(user, now));
    }

    /**
     * 진행 중 세션을 종료하고 측정량을 유저의 누적 잔여에서 차감한다.
     *
     * @throws IllegalStateException 진행 중 세션이 없거나 유저 타이머가 없는 경우
     */
    public ReadingSession stop(User user, Instant now) {
        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user)
                .orElseThrow(() -> new IllegalStateException("no active session to stop"));
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user"));

        active.end(now);
        timer.deduct(active.getDurationSeconds());

        sessionRepository.save(active);
        timerRepository.save(timer);
        return active;
    }
}
