package com.booktimer.retention;

import com.booktimer.config.TossProperties;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 재참여(리텐션) 넛지의 <b>토스 미니앱 푸시 채널</b>. 7일 비활동 사용자에게 "다시 읽어볼까요?" 한 통을 보낸다.
 *
 * <p>{@link RetentionNudgeService}(이메일)와 <b>같은 트리거·같은 멱등 컬럼</b>을 쓰되 대상 조건과 발송 수단만
 * 다르다 — 채널 추상화를 두지 않은 이유는 실제 공통부가 "조회 + 루프 + 성공 시 마킹"뿐이고, 대상 조건(이메일
 * 동의·검증 vs 토스 연동)도 페이로드(HTML 본문·수신거부 토큰 vs 템플릿 코드)도 겹치지 않기 때문이다.
 *
 * <ul>
 *   <li><b>다크런치 2중 게이트</b>: 템플릿 코드 미설정 → 조회조차 안 한다(콘솔 검수 전 5004 방지).
 *       메신저 게이트 OFF → 클라이언트 빈이 없어 {@code Optional.empty}({@code GoalMetPushService}와 동일).</li>
 *   <li><b>멱등</b>: 발송 성공 시에만 {@link User#recordNudgeSent}. 이메일 넛지와 <b>같은 컬럼</b>을 공유하는 것이
 *       채널 간 중복 발송 방지다 — 한 비활동 구간엔 어느 채널이든 총 1통.</li>
 *   <li><b>per-user 격리</b>: 한 명이 터져도 나머지는 계속 보낸다({@code RetentionNudgeService}와 같은 계약).</li>
 * </ul>
 *
 * <p>알림 동의는 서버가 모른다 — 정본은 토스이고, 미동의 발송은 토스가 거부해 {@code sendMessage=false}로 끝난다.
 */
@Service
public class TossRetentionNudgeService {

    private static final Logger log = LoggerFactory.getLogger(TossRetentionNudgeService.class);

    private final ReadingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    /** 게이트 OFF(다크런치)면 빈이 없다 — 그래도 앱 컨텍스트는 떠야 하므로 Optional 주입. */
    private final Optional<TossMessengerClient> client;
    private final TossProperties properties;
    private final Clock clock;

    public TossRetentionNudgeService(ReadingSessionRepository sessionRepository,
                                     UserRepository userRepository,
                                     Optional<TossMessengerClient> client,
                                     TossProperties properties,
                                     Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 7일 비활동 + 토스 연동 사용자에게 재참여 푸시를 보내고, 성공한 사람에 발송 시각을 박는다(멱등).
     *
     * @return 실제로 발송에 성공한 사용자 수
     */
    @Transactional
    public int sendNudges() {
        String templateCode = properties.getMessenger().getRetentionTemplateCode();
        if (templateCode == null || templateCode.isBlank()) {
            return 0; // 콘솔 검수 전 다크런치 — 마킹도 안 해 점등 당일 첫 배치가 그대로 나간다
        }
        if (client.isEmpty()) {
            return 0; // 메신저 게이트 OFF
        }
        Instant now = clock.instant();
        Instant cutoff = now.minus(RetentionNudgeService.INACTIVITY_WINDOW); // 이메일 넛지와 같은 창(7일)
        int sent = 0;
        for (User user : sessionRepository.findTossNudgeTargets(cutoff)) {
            try {
                if (client.get().sendMessage(user.getTossUserKey(), templateCode.strip(), Map.of())) {
                    user.recordNudgeSent(now); // 성공한 사람만 멱등 시각 기록 — 실패는 다음 배치 재시도
                    userRepository.save(user);
                    sent++;
                }
                // ponytail: 미동의 유저는 토스가 FAIL을 줘 미마킹 → 비활동인 동안 매일 재시도된다.
                // 현 규모(일 1회 배치·대상 소수)에선 로그 소음뿐 — 실측으로 커지면 실패 사유 구분을 검토.
            } catch (RuntimeException e) {
                // 한 명 실패가 배치를 멈추지 않게 격리 — userKey가 새지 않게 식별자만 로그.
                log.warn("토스 재참여 넛지 실패(나머지는 계속) — userId={}: {}", user.getId(), e.toString());
            }
        }
        log.info("토스 재참여 넛지 배치 완료 — {}명 발송", sent);
        return sent;
    }
}
