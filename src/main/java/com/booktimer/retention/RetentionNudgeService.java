package com.booktimer.retention;

import com.booktimer.email.EmailSender;
import com.booktimer.email.EmailTokenService;
import com.booktimer.email.EmailTokenType;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 재참여(리텐션) 넛지 발송 오케스트레이션 (이메일 인프라 2단계 PR-2).
 *
 * <p>7일 비활동 + 동의·검증을 만족하는 사용자에게 "다시 읽어볼까요?" 메일을 <b>비활동 구간당 1회</b> 보낸다.
 * 대상 선정은 {@link ReadingSessionRepository#findNudgeTargets}(SQL 경계 전수 테스트)가, 여기선 발송·격리·멱등을 맡는다.
 *
 * <ul>
 *   <li><b>cutoff</b> = {@code now - 7일}. 주입된 {@link Clock}이 "지금"을 결정한다(N-010 — 테스트 고정).</li>
 *   <li><b>per-recipient 격리</b>: 한 수신자 발송이 터져도 try-catch로 나머지를 계속 보낸다(배치 중단 금지).</li>
 *   <li><b>멱등</b>: 발송 성공 시에만 {@link User#recordNudgeSent}로 시각을 박아 이 구간 재발송을 막는다.
 *       실패한 수신자는 시각이 안 박혀 다음 배치에서 다시 대상이 된다.</li>
 *   <li><b>법무</b>: 제목 (광고) 접두, 본문에 무료·간편 구독해지 링크(UNSUBSCRIBE 토큰) 포함(정보통신망법 §50).</li>
 * </ul>
 *
 * <p>실발송 여부는 {@link EmailSender} 구현체({@code booktimer.email.enabled})가 가른다 — 꺼짐이면 로그만(메일 0).
 * 배치는 요청 스레드 밖(스케줄러)이라 동기 발송 후 성공을 확인해 멱등 시각을 박는다.
 */
@Service
public class RetentionNudgeService {

    private static final Logger log = LoggerFactory.getLogger(RetentionNudgeService.class);

    /** 비활동 판정 창 — 마지막 활동이 이보다 오래면 넛지 대상(7일 윈도우 모델과 정합). */
    static final Duration INACTIVITY_WINDOW = Duration.ofDays(7);

    /** (광고) 접두는 정보통신망법 §50 의무 — 제목 맨 앞에 둔다. */
    private static final String SUBJECT = "(광고) [BookTimer] 책 읽기, 오늘 다시 시작해볼까요?";

    private final ReadingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final EmailTokenService tokenService;
    private final Clock clock;
    private final String baseUrl;

    public RetentionNudgeService(ReadingSessionRepository sessionRepository,
                                 UserRepository userRepository,
                                 EmailSender emailSender,
                                 EmailTokenService tokenService,
                                 Clock clock,
                                 @Value("${booktimer.base-url:http://localhost:8080}") String baseUrl) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.tokenService = tokenService;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    /**
     * 넛지 대상을 조회해 각자에게 메일을 보내고, 성공한 수신자에 발송 시각을 박는다(멱등).
     *
     * @return 실제로 발송에 성공한 수신자 수
     */
    @Transactional
    public int sendNudges() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(INACTIVITY_WINDOW);
        List<User> targets = sessionRepository.findNudgeTargets(cutoff);
        int sent = 0;
        for (User user : targets) {
            try {
                String rawToken = tokenService.issue(user, EmailTokenType.UNSUBSCRIBE);
                String unsubscribeLink = baseUrl + "/unsubscribe?token="
                        + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
                emailSender.send(user.getEmail(), SUBJECT, buildBody(user, unsubscribeLink));
                user.recordNudgeSent(now); // 성공한 수신자만 멱등 시각 기록
                userRepository.save(user);
                sent++;
            } catch (RuntimeException e) {
                // 한 명 실패가 배치를 멈추지 않게 격리 — 본문·토큰이 새지 않게 식별자만 로그.
                log.warn("재참여 넛지 발송 실패(나머지는 계속) — userId={}", user.getId());
            }
        }
        log.info("재참여 넛지 배치 완료 — 대상 {}명 중 {}명 발송", targets.size(), sent);
        return sent;
    }

    /** 넛지 메일 HTML 본문 — 발신자 명칭·연락처(§50 ④ — 처리방침과 동일 문의처)와 무료·간편 구독해지 링크(③)를 포함한다. */
    private String buildBody(User user, String unsubscribeLink) {
        return """
                <p>%s님, 오랜만이에요.</p>
                <p>요즘 BookTimer로 책을 펼치지 못하셨네요. 하루 딱 몇 분이면 충분해요 — 오늘 다시 한 페이지만 함께 시작해볼까요?</p>
                <p><a href="%s/">BookTimer 열기</a></p>
                <hr>
                <p style="font-size:12px;color:#888;">
                    이 메일은 회원님이 수신에 동의하신 (광고) 정보입니다. 발신: BookTimer · 문의: tlatldhs0504@naver.com<br>
                    더 이상 받고 싶지 않으시면 여기서 <a href="%s">수신 거부</a>하실 수 있어요(무료).
                </p>
                """.formatted(escape(user.getNickname()), escape(baseUrl), unsubscribeLink);
    }

    /** 닉네임·URL 등 본문에 들어가는 값의 최소 이스케이프(XSS 방지). */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
