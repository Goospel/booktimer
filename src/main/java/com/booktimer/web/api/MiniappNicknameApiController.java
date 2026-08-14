package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import com.booktimer.user.UserSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 미니앱(앱인토스) 닉네임 변경 API.
 *
 * <p>토스로 시작한 계정은 닉네임이 전원 기본값 "토스유저"인데 바꿀 길이 없었다 — 변경 경로가 웹
 * {@code /settings} 하나뿐인데 그 계정들은 <b>비밀번호가 없어 웹 로그인 자체가 불가능</b>하기 때문이다.
 * 핸들({@link MiniappHandleApiController})이 "발견"을 열었다면 닉네임은 발견된 뒤의 "표시"를 맡는다.
 *
 * <p>부분 갱신 PATCH 대신 <b>목적별 POST</b>다 — 기존 미니앱 엔드포인트({@code /goal}·{@code /handle}·
 * {@code /debt-waiver})와 같은 계약이고, "안 보낸 필드"와 "지우려는 필드"가 헷갈릴 자리가 없다.
 *
 * <p>인증은 Bearer 토큰이다 — {@code /api/**} + {@code Authorization: Bearer}는 미니앱 전용 체인이 맡는다
 * (SecurityConfig §2.3 — 새 경로 등록 불필요).
 */
@RestController
public class MiniappNicknameApiController {

    private final CurrentUserService currentUserService;
    private final UserSettingsService settingsService;

    public MiniappNicknameApiController(CurrentUserService currentUserService,
                                        UserSettingsService settingsService) {
        this.currentUserService = currentUserService;
        this.settingsService = settingsService;
    }

    /** @return 200 저장된 닉네임 / 400 공백·상한 초과 / 401 토큰 없음·무효(체인이 처리) */
    @PostMapping("/api/miniapp/nickname")
    public ResponseEntity<NicknameResponse> updateNickname(Principal principal,
                                                           @RequestBody NicknameRequest request) {
        User user = currentUserService.resolve(principal);
        settingsService.updateNickname(user, request.nickname());
        // 저장된 값을 돌려준다 — 클라이언트가 재조회 없이 표시를 갱신한다(핸들 API와 같은 계약).
        return ResponseEntity.ok(new NicknameResponse(user.getNickname()));
    }

    // 미니앱은 에러 본문 평문을 그대로 사용자에게 띄운다(`api.ts errorMessage()`) — 문구가 곧 안내다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalid(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("닉네임은 공백 없이 " + User.NICKNAME_MAX_LENGTH + "자 이내로 입력해 주세요.");
    }

    public record NicknameRequest(String nickname) {
    }

    public record NicknameResponse(String nickname) {
    }
}
