package com.booktimer.study;

import com.booktimer.user.Role;
import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 호출 게이트 — 승인된 사용자만 통과한다.
 *
 * <p>DB가 필요 없는 순수 판정이라 서비스를 그대로 만들어 잰다(리포지토리는 이 메서드가 안 쓴다).
 * 게이트가 <b>지금은 호출부가 없다</b> — AI 엔드포인트가 다음 판에 붙는다. 그래도 여기서 규칙을 못
 * 박아 두는 이유는, 그 판의 첫 테스트가 「미승인 403」이고 그 답이 여기 하나여야 하기 때문이다.
 */
class StudyAiAccessServiceTest {

    private final StudyAiAccessService service = new StudyAiAccessService(null);

    private static User userIn(StudyAiAccess state) {
        User user = User.of("a@booktimer.com", "hash", "닉", "Asia/Seoul", Role.USER);
        Instant now = Instant.parse("2026-09-03T01:00:00Z");
        switch (state) {
            case NONE -> { }
            case PENDING -> user.requestStudyAi(now);
            case APPROVED -> {
                user.requestStudyAi(now);
                user.approveStudyAi(now);
            }
            case REJECTED -> {
                user.requestStudyAi(now);
                user.rejectStudyAi(now);
            }
        }
        return user;
    }

    @ParameterizedTest
    @EnumSource(value = StudyAiAccess.class, names = {"NONE", "PENDING", "REJECTED"})
    @DisplayName("승인 상태가 아니면 403 — 「꺼져 있다」가 아니라 「승인이 필요하다」로 말한다")
    void requireApproved_notApproved_forbids(StudyAiAccess state) {
        User user = userIn(state);

        assertThatThrownBy(() -> service.requireApproved(user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("승인")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("승인된 사용자는 통과한다")
    void requireApproved_approved_passes() {
        assertThatCode(() -> service.requireApproved(userIn(StudyAiAccess.APPROVED)))
                .doesNotThrowAnyException();
    }

    /**
     * 신청 게이트 — 이메일 미검증 계정은 대기 큐에 들어가지 못한다(리뷰 S-4).
     *
     * <p>DB 없이 잴 수 있는 것은 <b>검사가 전이·저장보다 앞</b>이기 때문이다(리포지터리가 {@code null}인데
     * 통과하면 NPE가 난다 — 그 자체가 게이트가 없다는 신호다). 검증된 계정이 통과하는 <b>양성 대조군</b>은
     * 저장이 필요해 {@code StudyAiApprovalCapTest}가 든다.
     */
    @Test
    @DisplayName("이메일 미검증이면 신청이 403 — 상태도 흔들지 않는다")
    void request_unverifiedEmail_forbids() {
        User user = userIn(StudyAiAccess.NONE); // User.of는 emailVerified=false로 시작한다

        assertThatThrownBy(() -> service.request(user, Instant.parse("2026-09-13T01:00:00Z")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이메일 인증 후 신청할 수 있어요")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(user.getStudyAiAccess()).isEqualTo(StudyAiAccess.NONE);
    }
}
