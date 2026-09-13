package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.study.StudyAiAccessService;
import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;

/**
 * AI 기능 신청 — 사용자가 스스로 밟는 유일한 승인 경로다(수락·거절·회수는 관리자 몫).
 *
 * <p>세션 인증 + CSRF({@code X-CSRF-TOKEN})다 — Authorization 헤더가 없는 {@code /api/**}는 세션 체인으로
 * 흐른다(설계 §1.2, {@link StudyPlanApiController} javadoc).
 */
@RestController
public class StudyAiAccessApiController {

    private final CurrentUserService currentUserService;
    private final StudyAiAccessService accessService;
    private final Clock clock;

    public StudyAiAccessApiController(CurrentUserService currentUserService,
                                      StudyAiAccessService accessService,
                                      Clock clock) {
        this.currentUserService = currentUserService;
        this.accessService = accessService;
        this.clock = clock;
    }

    /**
     * 신청한다. 거절·회수당한 뒤 다시 누르는 것도 여기다(쿨다운 없음 — 설계 §2.6).
     *
     * @return 200 {@link AccessState} / 409 이미 대기 중이거나 승인된 상태
     */
    @PostMapping("/api/study/ai-access/request")
    public ResponseEntity<AccessState> request(Principal principal) {
        User user = accessService.request(currentUserService.resolve(principal), clock.instant());
        return ResponseEntity.ok(new AccessState(user.getStudyAiAccess(), user.getStudyAiAccessAt()));
    }

    /**
     * 전이 규칙 위반({@link User#requestStudyAi})을 409로 옮긴다 — 대기 큐를 두 번 채우거나, 이미 받은
     * 승인을 다시 대기로 되돌리는 요청이 조용히 성공하지 않게 한다.
     *
     * <p>{@code CurrentUserService.resolve}의 사용자-없음은
     * {@link com.booktimer.security.AuthenticatedUserNotFoundException}이라 <b>여기 안 걸린다</b>(전역 500) —
     * 서버 결함을 「이미 신청했다」는 거짓 안내로 옮기지 않기 위한 분리다.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleAlreadyRequested(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 신청했거나 승인된 상태예요");
    }

    /**
     * 403의 <b>한국어 사유를 본문으로</b> 돌려준다({@link StudyPlanApiController}와 같은 규약).
     *
     * <p>없으면 전역 처리기({@code GlobalExceptionHandler})가 {@code error.html}을 렌더해 HTML 문서 전체가
     * 본문이 되고, 화면의 {@code errorMessage}는 「{@code <}로 시작하면 못 믿는다」 규칙으로 그걸 버려
     * 폴백 문구만 남는다 — 「이메일 인증을 하면 된다」는 <b>행동할 수 있는</b> 사유가 사용자에게 안 닿는다.
     *
     * <p>{@code text/plain} + charset을 못 박는 이유도 그쪽과 같다: 협상에 맡기면 {@code Accept: text/html}
     * 요청에서 {@code text/html}로 나가고, charset을 빼면 한글이 깨진다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getReason());
    }

    public record AccessState(StudyAiAccess aiAccess, Instant aiAccessAt) {
    }
}
