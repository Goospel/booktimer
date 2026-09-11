package com.booktimer.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 무차별 대입(brute-force) 방어 코어 — 키(클라이언트 IP)별 연속 실패 추적.
 *
 * <p>Spring Security는 레이트리밋/계정 잠금을 기본 제공하지 않는다(N-026). 이 서비스가 인메모리로
 * 키별 연속 실패 횟수를 세고, {@link #MAX_ATTEMPTS}회에 도달하면 {@link #LOCKOUT} 동안 잠근다.
 * 성공하면 카운터를 초기화하고, 마지막 실패로부터 잠금 시간이 지나면 자동으로 풀린다.
 *
 * <p><b>키를 이메일이 아니라 IP로 두는 이유</b>: 이메일을 키로 하면 공격자가 피해자 이메일로 일부러
 * 실패시켜 그 계정을 잠가버리는 DoS가 가능하다. IP 기준이면 공격 출처만 차단된다.
 * (분산 출처에는 약하므로 앞단 WAF 레이트리밋과 함께 쓰는 다층 방어의 한 겹이다.)
 *
 * <p>시간은 주입된 {@link Clock}으로 읽어 테스트에서 결정적으로 만든다.
 */
@Service
public class LoginAttemptService {

    /** 잠금까지 허용하는 연속 실패 횟수. */
    public static final int MAX_ATTEMPTS = 5;

    /** 임계치 도달 후 잠금 유지 시간. */
    public static final Duration LOCKOUT = Duration.ofMinutes(15);

    /**
     * 이 크기를 넘으면 다음 실패 기록이 만료 키를 한 번 쓸어낸다. 키가 IP라 분산 출처(봇넷·프록시)는
     * 키를 무한히 만들 수 있는데, 만료된 항목은 <b>같은 키가 다시 오거나 {@link #isBlocked}로 조회될 때만</b>
     * 지워져 700MB 컨테이너에서 그대로 누수가 된다.
     */
    static final int SWEEP_THRESHOLD = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** 인증 실패를 기록한다. 마지막 실패가 잠금 시간보다 오래됐으면 카운터를 새로 시작한다(윈도우 리셋). */
    public void recordFailure(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (k, prev) -> {
            if (prev == null || isExpired(prev, now)) {
                return new Attempt(1, now);
            }
            return new Attempt(prev.count() + 1, now);
        });
        sweepIfCrowded(now);
    }

    /**
     * 맵이 커졌을 때만 만료 키를 전수 삭제한다 — 잠금이 풀린 항목은 같은 키가 다시 오거나 조회될 때만
     * 지워져, 분산 출처의 IP 키가 쌓이면 그대로 메모리 누수가 된다.
     */
    // ponytail: 크기 트리거 전수 sweep — 10k 초과 시 O(n) 1회. 분산 상한(Redis)은 인스턴스가 늘 때.
    private void sweepIfCrowded(Instant now) {
        if (attempts.size() > SWEEP_THRESHOLD) {
            attempts.entrySet().removeIf(e -> isExpired(e.getValue(), now));
        }
    }

    /** 테스트 계측용 — 현재 보관 중인 키 수(sweep이 실제로 줄이는지 재는 유일한 수단). */
    int sizeForTest() {
        return attempts.size();
    }

    /** 인증 성공을 기록한다 — 해당 키의 실패 카운터를 비운다. */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    /** 해당 키가 현재 잠겨 있는지. 잠금 시간이 지났으면 자동으로 풀린 것으로 본다. */
    public boolean isBlocked(String key) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return false;
        }
        Instant now = clock.instant();
        if (isExpired(a, now)) {
            attempts.remove(key);
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    private boolean isExpired(Attempt a, Instant now) {
        return a.lastFailure().plus(LOCKOUT).isBefore(now);
    }

    private record Attempt(int count, Instant lastFailure) {
    }
}
