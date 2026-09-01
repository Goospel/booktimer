package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;

/**
 * 공부 측정 start/stop JSON API — 미니앱 「공부」 모드가 쓰는 유일한 서버 문이다.
 *
 * <p>에러 계약은 독서({@code /api/sessions/*})와 <b>글자 그대로 같다</b>: 409 = 중복 start / 무세션 stop.
 * 두 모드가 다른 말을 하면 클라이언트가 모드마다 다른 처리를 하게 된다.
 *
 * <p>인증 라우팅은 설정 변경이 필요 없다 — {@code SecurityConfig.isMiniappApiRequest}가 Bearer 헤더 붙은
 * {@code /api/**}를 미니앱 체인으로 보내므로 {@code /api/study/**}가 자동으로 커버된다.
 */
@RestController
public class StudyApiController {

    private final CurrentUserService currentUserService;
    private final StudySessionService studyService;
    private final Clock clock;

    public StudyApiController(CurrentUserService currentUserService,
                              StudySessionService studyService,
                              Clock clock) {
        this.currentUserService = currentUserService;
        this.studyService = studyService;
        this.clock = clock;
    }

    @PostMapping("/api/study/start")
    public ResponseEntity<StudyState> start(Principal principal) {
        User user = currentUserService.resolve(principal);
        Instant now = clock.instant();
        try {
            studyService.start(user, now);
        } catch (IllegalStateException e) {
            // 공부가 이미 돌고 있든 독서가 돌고 있든 사용자에겐 같은 사실이다 — 「지금 재는 중인 게 있다」.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 측정이 있습니다");
        }
        return ResponseEntity.ok(StudyState.of(studyService, user, now));
    }

    @PostMapping("/api/study/stop")
    public ResponseEntity<StudyState> stop(Principal principal) {
        User user = currentUserService.resolve(principal);
        Instant now = clock.instant();
        try {
            studyService.stop(user, now);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 측정이 없습니다");
        }
        return ResponseEntity.ok(StudyState.of(studyService, user, now));
    }

    /**
     * 공부 모드 화면 상태 — 이 셋이면 히어로가 다 그려진다(목표·게이지·부채가 없어 더 실을 것이 없다).
     *
     * <p>{@code todaySeconds}는 <b>완료 세션 합</b>이다 — 진행 중 몫은 클라이언트가 {@code activeStartedAt}
     * 으로 매초 더한다(독서 히어로와 같은 분업).
     */
    public record StudyState(boolean hasActiveSession, Instant activeStartedAt, long todaySeconds) {

        static StudyState of(StudySessionService service, User user, Instant now) {
            StudySession active = service.activeSession(user);
            return new StudyState(
                    active != null,
                    active == null ? null : active.getStartedAt(),
                    service.todaySeconds(user, now));
        }
    }
}
