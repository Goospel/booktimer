package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 기능 승인 상태 전이 — 4상태 × 4동작 전수 (DB 무관).
 *
 * <p>전이 규칙이 여기 한 곳에만 있는 것이 요점이다(설계 §2.6): 신청 API도 관리자 화면도 이 메서드를
 * 부를 뿐이라, 「거절당한 사람이 다시 신청할 수 있는가」 같은 질문의 답이 두 곳으로 갈라지지 않는다.
 * 표를 파라미터라이즈해 <b>허용 4칸과 금지 12칸</b>을 모두 실행한다 — 금지 쪽을 빼면 "아무 전이나 되는"
 * 구현도 통과한다.
 */
class UserStudyAiAccessTest {

    private static final Instant NOW = Instant.parse("2026-09-03T01:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-09-01T01:00:00Z");

    /** 전이 동작 — 이름은 실패 메시지에 그대로 뜬다. */
    private record Action(String name, BiConsumer<User, Instant> apply) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Action REQUEST = new Action("request", User::requestStudyAi);
    private static final Action APPROVE = new Action("approve", User::approveStudyAi);
    private static final Action REJECT = new Action("reject", User::rejectStudyAi);
    private static final Action REVOKE = new Action("revoke", User::revokeStudyAi);

    private static User userIn(StudyAiAccess state) {
        User user = User.of("a@booktimer.com", "hash", "닉", "Asia/Seoul", Role.USER);
        switch (state) {
            case NONE -> { }
            case PENDING -> user.requestStudyAi(EARLIER);
            case APPROVED -> {
                user.requestStudyAi(EARLIER);
                user.approveStudyAi(EARLIER);
            }
            case REJECTED -> {
                user.requestStudyAi(EARLIER);
                user.rejectStudyAi(EARLIER);
            }
        }
        return user;
    }

    /** 허용된 4칸: (시작 상태, 동작, 결과 상태). */
    static List<Arguments> allowed() {
        return List.of(
                Arguments.of(StudyAiAccess.NONE, REQUEST, StudyAiAccess.PENDING),
                Arguments.of(StudyAiAccess.REJECTED, REQUEST, StudyAiAccess.PENDING),
                Arguments.of(StudyAiAccess.PENDING, APPROVE, StudyAiAccess.APPROVED),
                Arguments.of(StudyAiAccess.PENDING, REJECT, StudyAiAccess.REJECTED),
                Arguments.of(StudyAiAccess.APPROVED, REVOKE, StudyAiAccess.REJECTED));
    }

    /** 나머지 전부 — 허용 표의 여집합을 <b>계산해서</b> 만든다(손으로 적으면 빠뜨린다). */
    static List<Arguments> forbidden() {
        List<Arguments> rows = new ArrayList<>();
        for (StudyAiAccess from : StudyAiAccess.values()) {
            for (Action action : List.of(REQUEST, APPROVE, REJECT, REVOKE)) {
                boolean isAllowed = allowed().stream()
                        .anyMatch(a -> a.get()[0] == from && a.get()[1] == action);
                if (!isAllowed) {
                    rows.add(Arguments.of(from, action));
                }
            }
        }
        return rows;
    }

    @ParameterizedTest(name = "{0} + {1} → {2}")
    @DisplayName("허용된 전이는 상태와 전이 시각을 함께 갱신한다")
    @MethodSource("allowed")
    void allowedTransition_movesStateAndStampsTime(StudyAiAccess from, Action action, StudyAiAccess to) {
        User user = userIn(from);

        action.apply().accept(user, NOW);

        assertThat(user.getStudyAiAccess()).isEqualTo(to);
        assertThat(user.getStudyAiAccessAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "{0} + {1} → 거부")
    @DisplayName("허용 표에 없는 전이는 IllegalStateException이고 상태·시각이 그대로다")
    @MethodSource("forbidden")
    void forbiddenTransition_throwsAndKeepsState(StudyAiAccess from, Action action) {
        User user = userIn(from);
        Instant before = user.getStudyAiAccessAt();

        assertThatThrownBy(() -> action.apply().accept(user, NOW))
                .isInstanceOf(IllegalStateException.class);

        assertThat(user.getStudyAiAccess()).isEqualTo(from);
        assertThat(user.getStudyAiAccessAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("새 사용자는 NONE이고 전이 시각이 없다 — 아무도 자동 승인되지 않는다")
    void newUser_startsAtNone() {
        User user = User.of("a@booktimer.com", "hash", "닉", "Asia/Seoul", Role.USER);

        assertThat(user.getStudyAiAccess()).isEqualTo(StudyAiAccess.NONE);
        assertThat(user.getStudyAiAccessAt()).isNull();
    }

    @Test
    @DisplayName("거절·회수 뒤 재신청은 즉시 가능하다(쿨다운 없음)")
    void rejected_canRequestAgainImmediately() {
        User user = userIn(StudyAiAccess.APPROVED);
        user.revokeStudyAi(EARLIER);

        user.requestStudyAi(NOW);

        assertThat(user.getStudyAiAccess()).isEqualTo(StudyAiAccess.PENDING);
        assertThat(user.getStudyAiAccessAt()).isEqualTo(NOW);
    }
}
