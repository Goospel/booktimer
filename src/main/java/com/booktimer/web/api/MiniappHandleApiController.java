package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.AccountService;
import com.booktimer.user.LoginIdAlreadyExistsException;
import com.booktimer.user.OnboardingService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 미니앱(앱인토스) 핸들 설정 API — 공개 @핸들({@code login_id}) 만들기·바꾸기.
 *
 * <p>토스로 시작한 계정은 {@code login_id=null}이라 소셜 전 경로(검색·친구추천·팔로우 목록·스토리 피드·
 * 프로필)의 노출 필터에 걸려 <b>영구히 발견되지 않는다</b>. 웹 온보딩은 비밀번호가 없는 이 계정들에게
 * 닿지 않으므로(웹 로그인 자체가 불가) 미니앱 안에 만들 자리가 필요했다 — 그게 이 엔드포인트다.
 *
 * <p>하루 목표는 여기서 받지 않는다 — {@link MiniappGoalApiController}({@code /api/miniapp/goal})가 이미
 * 담당한다. 핸들은 <b>평생 1번만 바꿀 수 있어</b> 첫 진입에서 강요하지 않고, 소셜 탭에서 본인이 원할 때 만든다.
 *
 * <p>인증은 Bearer 토큰이다 — {@code /api/**} + {@code Authorization: Bearer}는 미니앱 전용 체인이 맡는다
 * (SecurityConfig §2.3 — 새 경로 등록 불필요).
 */
@RestController
public class MiniappHandleApiController {

    private final CurrentUserService currentUserService;
    private final OnboardingService onboardingService;
    private final AccountService accountService;

    public MiniappHandleApiController(CurrentUserService currentUserService,
                                      OnboardingService onboardingService,
                                      AccountService accountService) {
        this.currentUserService = currentUserService;
        this.onboardingService = onboardingService;
        this.accountService = accountService;
    }

    /** @return 200 정규화된 핸들 / 400 형식·예약어 / 409 중복·이미 있음 / 401 토큰 없음·무효(체인이 처리) */
    @PostMapping("/api/miniapp/handle")
    public ResponseEntity<HandleResponse> createHandle(Principal principal, @RequestBody HandleRequest request) {
        User user = currentUserService.resolve(principal);
        onboardingService.assignHandle(user, request.loginId());
        // 정규화된 값을 돌려준다 — 클라이언트가 재조회 없이 "내 책방" 상태로 전환한다.
        return ResponseEntity.ok(new HandleResponse(user.getLoginId()));
    }

    /**
     * 아이디를 <b>평생 1회</b> 바꾼다 — 웹에 로그인할 수 없는 토스 전용 계정의 유일한 변경 경로.
     *
     * <p>소진·형식은 <b>이 메서드 안에서</b> 잡는다: 클래스 레벨 핸들러는 이미 <b>생성</b> 의미로 점유돼 있어
     * (ISE=이미 있음, IAE=형식) 그대로 두면 변경 실패 문구가 뭉개진다. 중복만 클래스 핸들러에 떨어뜨린다 —
     * "이미 사용 중"은 생성·변경 어느 쪽에서도 정확하다.
     *
     * @return 200 정규화된 새 핸들 / 400 형식·예약어·지금과 동일 / 409 소진·중복 / 401 체인이 처리
     */
    @PostMapping("/api/miniapp/handle/change")
    public ResponseEntity<?> changeHandle(Principal principal, @RequestBody HandleRequest request) {
        User user = currentUserService.resolve(principal);
        try {
            accountService.changeLoginId(user, request.loginId());
        } catch (IllegalStateException e) {
            // 소진 — 핸들이 아예 없는 계정(정상 UI로는 도달 불가)도 같은 문구로 흡수한다.
            return ResponseEntity.status(HttpStatus.CONFLICT).body("아이디 변경은 평생 1번이에요. 이미 사용했어요.");
        } catch (IllegalArgumentException e) {
            // 형식·예약어·지금과 동일을 한 문구로 병합 — 어느 쪽이든 사용자가 할 일은 "다른 값을 치는 것" 하나다.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("사용할 수 없는 아이디예요. 영문 소문자·숫자·밑줄(_) 3~20자, 지금 아이디와 달라야 해요.");
        }
        return ResponseEntity.ok(new HandleResponse(user.getLoginId()));
    }

    // 미니앱은 에러 본문 평문을 그대로 사용자에게 띄운다(`api.ts errorMessage()`) — 문구가 곧 안내다.

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleMalformed(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("사용할 수 없는 아이디예요. 영문 소문자·숫자·밑줄(_) 3~20자로 지어 주세요.");
    }

    @ExceptionHandler(LoginIdAlreadyExistsException.class)
    public ResponseEntity<String> handleTaken(LoginIdAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 사용 중인 아이디예요. 다른 아이디를 지어 주세요.");
    }

    /**
     * 이미 핸들이 있는 계정(once-set 위반) — 정상 흐름에선 더블탭·두 기기 경합뿐이라 문구만 다르면 충분하다.
     * 「바꿀 수 없어요」라고는 하지 않는다 — {@link #changeHandle} 이후로는 거짓이다(평생 1회는 바꿀 수 있다).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleAlreadySet(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 아이디가 있어요.");
    }

    public record HandleRequest(String loginId) {
    }

    public record HandleResponse(String loginId) {
    }
}
