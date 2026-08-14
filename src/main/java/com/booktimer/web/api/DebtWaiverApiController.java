package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.session.GoalWaiverService;
import com.booktimer.user.User;
import com.booktimer.web.DashboardModel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

/**
 * 미니앱 리워드 광고 보상 API — {@code POST /api/miniapp/debt-waiver} (설계 §5.1-⑤).
 *
 * <p><b>요청 body가 없다</b> — 지울 날짜를 서버가 고르므로 클라이언트에 조작 표면이 아예 없다. 응답에는
 * 갱신된 {@link DashboardApiController.TimerState}를 함께 실어 홈의 밀린 시간·버튼 노출이 재조회 없이 갱신된다.
 *
 * <p>인증은 Bearer 토큰이다 — {@code /api/**} + {@code Authorization: Bearer}는 미니앱 전용 stateless 체인이
 * 맡아 401을 돌려준다({@code MiniappGoalApiController}와 같은 패턴, 추가 인증 작업 없음).
 *
 * <p><b>왜 별도 컨트롤러인가</b>: 실패 문구를 미니앱이 그대로 띄우려면 예외를 <b>평문 본문</b>으로 내려야 하는데
 * ({@code GlobalExceptionHandler}는 HTML {@code error} 뷰를 렌더한다), 그 {@code @ExceptionHandler}를
 * {@link DashboardApiController}에 달면 {@code IllegalStateException}을 던지는
 * {@code CurrentUserService.resolve}("인증 주체는 있으나 도메인 유저 없음" = 진짜 500)까지 409로 뒤바뀌어
 * 나머지 4개 엔드포인트의 진단성이 망가진다. 컨트롤러를 분리하면 그 사각이 이 엔드포인트 안에 갇힌다.
 */
@RestController
public class DebtWaiverApiController {

    private final CurrentUserService currentUserService;
    private final GoalWaiverService goalWaiverService;
    private final DashboardModel dashboardModel;

    public DebtWaiverApiController(CurrentUserService currentUserService,
                                   GoalWaiverService goalWaiverService,
                                   DashboardModel dashboardModel) {
        this.currentUserService = currentUserService;
        this.goalWaiverService = goalWaiverService;
        this.dashboardModel = dashboardModel;
    }

    /** @return 200 {waivedDate, waivedSeconds, timer} / 400 지울 날 없음 / 401 미인증(체인) / 409 중복 소거 race */
    @PostMapping("/api/miniapp/debt-waiver")
    public WaiveResponse waiveDebt(Principal principal) {
        User user = currentUserService.resolve(principal);
        GoalWaiverService.WaiveResult result = goalWaiverService.waive(user);
        return new WaiveResponse(result.waivedDate(), result.waivedSeconds(),
                DashboardApiController.TimerState.of(dashboardModel.computeLive(user),
                        goalWaiverService.availableFor(user)));
    }

    /**
     * 같은 날을 중복 소거 → 409. 두 탭이 동시에 같은 날을 집으면 {@code (user, waived_date)} 유니크가 잡는다.
     *
     * <p>{@code IllegalStateException}은 여기서 다루지 않는다 — 일일 상한이 없어진 뒤 이 경로에서 그 예외를
     * 던지는 곳은 {@code CurrentUserService.resolve}("인증 주체는 있으나 도메인 유저 없음" = 진짜 500)뿐이라,
     * 409로 뒤바꾸면 그 진단성이 사라진다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDuplicateWaive(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("방금 지워진 날이에요. 다시 시도해 주세요.");
    }

    /** 지울 밀린 날이 없음 → 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNothingToWaive(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * @param waivedDate    지워진 날짜
     * @param waivedSeconds 그 날 소거된 부채(초) — "N분을 지웠어요" 문구용
     * @param timer         갱신된 라이브 상태(부채·버튼 노출 즉시 반영)
     */
    public record WaiveResponse(LocalDate waivedDate, long waivedSeconds,
                                DashboardApiController.TimerState timer) {}
}
