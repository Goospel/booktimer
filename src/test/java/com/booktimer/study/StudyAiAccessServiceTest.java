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
}
