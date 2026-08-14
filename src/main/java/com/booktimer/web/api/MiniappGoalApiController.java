package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.OnboardingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 미니앱(앱인토스) 경량 온보딩 API — 하루 목표 설정 (설계 §2.4).
 *
 * <p>미니앱 온보딩은 <b>하루 목표만</b> 정한다. 웹 온보딩과 달리 공개 핸들({@code login_id})을 요구하지 않는다 —
 * 핸들은 한 번 정하면 불변이라 첫 진입에서 강요하면 마찰이 크고 되돌릴 수도 없다. 그래서 이 경로는
 * {@code completeOnboarding()}을 부르지 않는다(부르면 핸들 없는 계정이 DB CHECK를 위반한다).
 * 핸들은 나중에 본인이 원할 때 {@link MiniappHandleApiController}({@code /api/miniapp/handle})로 만든다.
 *
 * <p>인증은 Bearer 토큰이다 — {@code /api/**} + {@code Authorization: Bearer}는 미니앱 전용 체인이 맡는다
 * (SecurityConfig §2.3). 이후 목표 <b>변경</b>도 같은 엔드포인트를 쓴다(설정 화면이 없는 채널이라 구분이 무의미).
 */
@RestController
public class MiniappGoalApiController {

    private final CurrentUserService currentUserService;
    private final OnboardingService onboardingService;

    public MiniappGoalApiController(CurrentUserService currentUserService,
                                    OnboardingService onboardingService) {
        this.currentUserService = currentUserService;
        this.onboardingService = onboardingService;
    }

    /** @return 200 (본문 없음) / 400 목표가 음수 / 401 토큰 없음·무효(체인이 처리) */
    @PostMapping("/api/miniapp/goal")
    public ResponseEntity<Void> setGoal(Principal principal, @RequestBody MiniappGoalRequest request) {
        onboardingService.setDailyGoal(currentUserService.resolve(principal), request.dailyIncrementSeconds());
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /** @param dailyIncrementSeconds 하루 목표(초, 0 이상 — 0은 "목표 없음"으로 도메인이 허용한다) */
    public record MiniappGoalRequest(long dailyIncrementSeconds) {
    }
}
