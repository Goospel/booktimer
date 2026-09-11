package com.booktimer.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별·액션별 호출 제한(고정 윈도우) — 스팸 팔로우·검색 크롤링·신고 남용 방어 (sns-design §7.5·§9).
 *
 * <p>인증된 엔드포인트라 키를 IP가 아니라 <b>userId</b>로 둔다(NAT 뒤 다수 정상 사용자를 한 키로 묶지
 * 않으려고; 출처가 IP인 로그인 제한기 {@link LoginAttemptService}와 대비). 액션별 한도·윈도우는
 * {@link RateLimitAction}이 정의한다. 시간은 주입된 {@link Clock}으로 읽어 테스트에서 결정적으로 만든다.
 *
 * <p><b>한계</b>: 상태가 인메모리({@link ConcurrentHashMap})라 <b>인스턴스별</b>이다 — 다중 인스턴스/롤링
 * 배포 중에는 분산 우회가 가능하다(로그인 제한기와 같은 한계). 강한 보장이 필요하면 공유 저장소(Redis 등)나
 * 앞단 WAF 레이트리밋과 함께 쓰는 다층 방어의 한 겹으로 본다(plan.md 백로그).
 */
@Service
public class RateLimitService {

    /**
     * 이 크기를 넘으면 다음 호출이 만료 키를 한 번 쓸어낸다. IP를 키로 쓰는 액션(SIGNUP·PASSWORD_FORGOT·
     * TOSS_VERIFY 등)은 분산 출처가 키를 무한히 만들 수 있는데, 만료된 윈도우는 <b>같은 키가 다시 올 때만</b>
     * 리셋될 뿐 삭제되지 않아 700MB 컨테이너에서 그대로 누수가 된다.
     */
    static final int SWEEP_THRESHOLD = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    /**
     * (action, userId) 키의 현재 윈도우에서 이 호출을 허용할지 판정하고 카운트를 올린다.
     * 한도({@link RateLimitAction#limit()})까지는 {@code true}, 넘으면 {@code false}.
     * 윈도우가 지났으면 새 윈도우로 리셋한다.
     */
    public boolean allow(RateLimitAction action, long userId) {
        return allow(action, String.valueOf(userId));
    }

    /**
     * userId가 아직 없는 경로(예: 미니앱 토스 인증 — 우리 계정이 만들어지기 전이다)를 위해 <b>임의 문자열</b>을
     * 키로 받는 오버로드. 토스 로그인·연결은 토스 {@code userKey}를 키로 쓴다 — 인가코드가 일회성이라
     * 공격자도 매 시도마다 같은 신원을 다시 증명해야 하므로, 그 신원이 곧 제한 단위로 맞다.
     */
    public boolean allow(RateLimitAction action, String subject) {
        String key = action.name() + ":" + subject;
        Instant now = clock.instant();
        Window updated = windows.compute(key, (k, prev) -> {
            if (prev == null || prev.isExpired(now)) {
                return new Window(1, now, now.plus(action.window()));
            }
            return new Window(prev.count() + 1, prev.windowStart(), prev.expiresAt());
        });
        sweepIfCrowded(now);
        return updated.count() <= action.limit();
    }

    /**
     * 맵이 커졌을 때만 만료 키를 전수 삭제한다 — 만료 윈도우는 같은 키가 다시 올 때만 리셋될 뿐
     * 스스로 사라지지 않아, 분산 출처의 IP 키가 쌓이면 그대로 메모리 누수가 된다.
     */
    // ponytail: 크기 트리거 전수 sweep — 10k 초과 시 O(n) 1회. 분산 상한(Redis)은 인스턴스가 늘 때.
    private void sweepIfCrowded(Instant now) {
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().isExpired(now));
        }
    }

    /** 테스트 격리용 — 인메모리 윈도우 맵 초기화. 프로덕션 코드에서 호출하지 않는다. */
    public void clearForTest() {
        windows.clear();
    }

    /** 테스트 계측용 — 현재 보관 중인 키 수(sweep이 실제로 줄이는지 재는 유일한 수단). */
    int sizeForTest() {
        return windows.size();
    }

    private record Window(int count, Instant windowStart, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return expiresAt.isBefore(now);
        }
    }
}
