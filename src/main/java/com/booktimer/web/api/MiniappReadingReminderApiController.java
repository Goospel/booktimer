package com.booktimer.web.api;

import com.booktimer.retention.ReadingReminderService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.TossLinkConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * 미니앱 독서 알림 설정 저장(N3) — 방식(OFF·DAILY·REST)과 보낼 시각을 통째로 받는다.
 *
 * <p>미니앱은 토스 동의가 성공한 뒤에만 켬을 저장한다(동의 정본은 토스). 꺼진 채 시각만 바꾸는 저장도 받는다 —
 * 설정 화면에서 켜기 전에 시각을 먼저 고른다. 인증은 미니앱 Bearer 체인이다({@link MiniappGoalApiController}와 같음).
 */
@RestController
public class MiniappReadingReminderApiController {

    private static final MediaType TEXT_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final CurrentUserService currentUserService;
    private final ReadingReminderService service;

    public MiniappReadingReminderApiController(CurrentUserService currentUserService,
                                               ReadingReminderService service) {
        this.currentUserService = currentUserService;
        this.service = service;
    }

    /** @return 200 저장된 설정(View) / 400 값 오류 / 409 토스 미연결 / 401 토큰(체인) */
    @PostMapping("/api/miniapp/reading-reminder")
    public ReadingReminderService.View save(Principal principal, @RequestBody ReadingReminderRequest req) {
        return service.configure(currentUserService.resolve(principal), req.kind(), req.hour());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalid(IllegalArgumentException e) {
        // 고정 문구 — 입력을 되뇌지 않는다. text/plain + charset 명시(MiniappGoalApiController와 같은 이유: 반사 XSS·한글 깨짐)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(TEXT_UTF8).body("알림 설정 값이 올바르지 않아요.");
    }

    @ExceptionHandler(TossLinkConflictException.class)
    public ResponseEntity<String> handleNotLinked(TossLinkConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(TEXT_UTF8).body(e.getMessage());
    }

    /**
     * {@code kind}가 enum이 아니라 String인 이유: 모르는 값이 Jackson 역직렬화 오류가 되면 {@code /api/**} 400이
     * HTML error 뷰로 가 미니앱엔 상태 코드만 보인다 — String이면 모르는 값·키 없음(null)이 서비스의 IAE를 거쳐 평문 400이 된다.
     * {@code hour}는 원시 {@code int}라 키 없음·{@code null}·숫자 아닌 값은 Jackson 역직렬화 오류로 <b>HTML 400</b>이다
     * (평문이 아니다). 숫자이기만 하면 범위 밖(7·23 등)은 평문 400이다. 미니앱은 {@code REMINDER_HOURS}의 숫자만 보낸다.
     */
    public record ReadingReminderRequest(String kind, int hour) {}
}
