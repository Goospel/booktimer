package com.booktimer.feedback;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feedback 도메인 단위 테스트 (DB/컨텍스트 무관) — 사용자 피드백/문의.
 *
 * <p>생성 시 상태는 항상 SUBMITTED(미확인)에서 시작하고, 개발자가 markRead/markResolved로 진행시킨다.
 * markResolved는 읽음을 건너뛰어도 되지만, 이미 처리완료를 markRead로 되돌리지는 않는다(단조 진행).
 * 입력 검증(author·type null, 빈 제목·내용)은 팩토리가 막는다.
 */
class FeedbackTest {

    private User author() {
        return User.of("u@booktimer.com", "$2a$10$x", "유저", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("of: 생성하면 상태가 SUBMITTED(미확인)이고 입력이 보존된다")
    void of_defaultsToSubmitted() {
        Feedback f = Feedback.of(author(), FeedbackType.BUG, "버그 제목", "버그 내용");
        assertThat(f.getStatus()).isEqualTo(FeedbackStatus.SUBMITTED);
        assertThat(f.getType()).isEqualTo(FeedbackType.BUG);
        assertThat(f.getTitle()).isEqualTo("버그 제목");
        assertThat(f.getContent()).isEqualTo("버그 내용");
    }

    @Test
    @DisplayName("of: author·type null, 빈 제목·내용은 거부한다")
    void of_rejectsInvalid() {
        assertThatThrownBy(() -> Feedback.of(null, FeedbackType.BUG, "t", "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Feedback.of(author(), null, "t", "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Feedback.of(author(), FeedbackType.BUG, "   ", "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Feedback.of(author(), FeedbackType.BUG, "t", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markRead: 미확인 → 읽음")
    void markRead_submittedToRead() {
        Feedback f = Feedback.of(author(), FeedbackType.ETC, "t", "c");
        f.markRead();
        assertThat(f.getStatus()).isEqualTo(FeedbackStatus.READ);
    }

    @Test
    @DisplayName("markResolved: 읽음을 건너뛰고 바로 처리완료 가능")
    void markResolved_directly() {
        Feedback f = Feedback.of(author(), FeedbackType.ETC, "t", "c");
        f.markResolved();
        assertThat(f.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
    }

    @Test
    @DisplayName("markRead: 이미 처리완료면 읽음으로 되돌리지 않는다(단조 진행)")
    void markRead_doesNotDowngradeResolved() {
        Feedback f = Feedback.of(author(), FeedbackType.ETC, "t", "c");
        f.markResolved();
        f.markRead();
        assertThat(f.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
    }

    @Test
    @DisplayName("FeedbackType.from: 알 수 없는/빈 유형은 ETC로 폴백")
    void type_from_fallback() {
        assertThat(FeedbackType.from(null)).isEqualTo(FeedbackType.ETC);
        assertThat(FeedbackType.from("bug")).isEqualTo(FeedbackType.BUG);
        assertThat(FeedbackType.from("나쁜값")).isEqualTo(FeedbackType.ETC);
    }

    @Test
    @DisplayName("applyReply: 답장을 달면 미확인 → 읽음(자동), 답장 보존")
    void applyReply_autoMarksRead() {
        Feedback f = Feedback.of(author(), FeedbackType.BUG, "t", "c");
        f.applyReply("확인했습니다. 다음 배포에 반영할게요.");
        assertThat(f.getReply()).isEqualTo("확인했습니다. 다음 배포에 반영할게요.");
        assertThat(f.getStatus()).isEqualTo(FeedbackStatus.READ);
    }

    @Test
    @DisplayName("applyReply: 이미 처리완료면 답장만 갱신, 상태는 유지(되돌리지 않음)")
    void applyReply_keepsResolved() {
        Feedback f = Feedback.of(author(), FeedbackType.BUG, "t", "c");
        f.markResolved();
        f.applyReply("추가로 안내드립니다.");
        assertThat(f.getReply()).isEqualTo("추가로 안내드립니다.");
        assertThat(f.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
    }

    @Test
    @DisplayName("applyReply: 빈 답장은 거부한다")
    void applyReply_rejectsBlank() {
        Feedback f = Feedback.of(author(), FeedbackType.BUG, "t", "c");
        assertThatThrownBy(() -> f.applyReply("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.applyReply(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FeedbackType.parse: 유효 유형만 반환, 없음/빈/잘못된 값은 null(필터 없음)")
    void type_parse_nullForFilter() {
        assertThat(FeedbackType.parse(null)).isNull();
        assertThat(FeedbackType.parse("  ")).isNull();
        assertThat(FeedbackType.parse("나쁜값")).isNull();
        assertThat(FeedbackType.parse("bug")).isEqualTo(FeedbackType.BUG);
        assertThat(FeedbackType.parse("SUGGESTION")).isEqualTo(FeedbackType.SUGGESTION);
    }
}
