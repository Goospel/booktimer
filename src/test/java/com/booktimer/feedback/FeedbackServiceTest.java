package com.booktimer.feedback;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FeedbackService 통합 테스트 (실제 빈 + H2) — 사용자 피드백/문의.
 *
 * <p>핵심은 노출 스코핑과 IDOR이다: {@code myFeedback}은 작성자 본인 글만 반환하고,
 * {@code deleteOwn}은 남의 글 id로는 삭제하지 못한다(존재 누설 없이 무시). 관리자 삭제는 작성자 무관.
 */
@SpringBootTest
@Transactional
class FeedbackServiceTest {

    @Autowired
    private FeedbackService feedbackService;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private UserRepository userRepository;

    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$x", "유저", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    @Test
    @DisplayName("submit: 1행 저장, 상태 SUBMITTED, 입력 보존")
    void submit_savesSubmitted() {
        User me = user("a@booktimer.com", "alpha");

        Feedback f = feedbackService.submit(me, FeedbackType.BUG, "안 됨", "버튼이 안 눌려요");

        assertThat(f.getId()).isNotNull();
        assertThat(f.getStatus()).isEqualTo(FeedbackStatus.SUBMITTED);
        assertThat(feedbackRepository.findById(f.getId())).get()
                .extracting(Feedback::getTitle, Feedback::getType)
                .containsExactly("안 됨", FeedbackType.BUG);
    }

    @Test
    @DisplayName("myFeedback: 내 글만 반환하고 남의 글은 섞이지 않는다(스코핑)")
    void myFeedback_onlyMine() {
        User me = user("me@booktimer.com", "mine");
        User other = user("other@booktimer.com", "other");
        Feedback mineF = feedbackService.submit(me, FeedbackType.ETC, "내 문의", "내용");
        Feedback otherF = feedbackService.submit(other, FeedbackType.ETC, "남의 문의", "내용");

        var list = feedbackService.myFeedback(me);

        assertThat(list).extracting(Feedback::getId).containsExactly(mineF.getId());
        assertThat(list).extracting(Feedback::getId).doesNotContain(otherF.getId());
    }

    @Test
    @DisplayName("markRead → markResolved: id로 상태를 진행시킨다")
    void markRead_then_resolved() {
        User me = user("m@booktimer.com", "mikey");
        Feedback f = feedbackService.submit(me, FeedbackType.SUGGESTION, "제안", "이렇게 해주세요");

        feedbackService.markRead(f.getId());
        assertThat(feedbackRepository.findById(f.getId())).get()
                .extracting(Feedback::getStatus).isEqualTo(FeedbackStatus.READ);

        feedbackService.markResolved(f.getId());
        assertThat(feedbackRepository.findById(f.getId())).get()
                .extracting(Feedback::getStatus).isEqualTo(FeedbackStatus.RESOLVED);
    }

    @Test
    @DisplayName("deleteOwn: 본인 글은 삭제, 남의 글 id면 삭제 안 함(IDOR 방어)")
    void deleteOwn_idorGuard() {
        User me = user("d1@booktimer.com", "delone");
        User other = user("d2@booktimer.com", "deltwo");
        Feedback otherF = feedbackService.submit(other, FeedbackType.ETC, "남의 글", "내용");

        // me가 남의 글 id로 삭제 시도 → 아무 일도 안 일어남(존재 누설 없음)
        feedbackService.deleteOwn(otherF.getId(), me);
        assertThat(feedbackRepository.findById(otherF.getId())).isPresent();

        // 본인 글은 정상 삭제
        Feedback mineF = feedbackService.submit(me, FeedbackType.ETC, "내 글", "내용");
        feedbackService.deleteOwn(mineF.getId(), me);
        assertThat(feedbackRepository.findById(mineF.getId())).isEmpty();
    }

    @Test
    @DisplayName("deleteByAdmin: 작성자 무관하게 삭제(스팸 정리)")
    void deleteByAdmin_any() {
        User other = user("ad@booktimer.com", "adspam");
        Feedback f = feedbackService.submit(other, FeedbackType.ETC, "스팸", "광고");

        feedbackService.deleteByAdmin(f.getId());

        assertThat(feedbackRepository.findById(f.getId())).isEmpty();
    }

    @Test
    @DisplayName("reply: 답장을 저장하고 자동으로 읽음 처리한다")
    void reply_savesAndAutoReads() {
        User me = user("rp@booktimer.com", "replyuser");
        Feedback f = feedbackService.submit(me, FeedbackType.BUG, "버그", "내용");

        feedbackService.reply(f.getId(), "확인했어요. 곧 고칠게요.");

        assertThat(feedbackRepository.findById(f.getId())).get().satisfies(saved -> {
            assertThat(saved.getReply()).isEqualTo("확인했어요. 곧 고칠게요.");
            assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.READ);
        });
    }

    @Test
    @DisplayName("feedbackRows(filter): 유형으로 거르고, null이면 전체")
    void feedbackRows_filtersByType() {
        User a = user("f1@booktimer.com", "filteruser");
        feedbackService.submit(a, FeedbackType.BUG, "버그글", "내용");
        feedbackService.submit(a, FeedbackType.SUGGESTION, "제안글", "내용");

        assertThat(feedbackService.feedbackRows(FeedbackType.BUG))
                .extracting(AdminFeedbackRow::title).containsExactly("버그글");
        assertThat(feedbackService.feedbackRows(null))
                .extracting(AdminFeedbackRow::title).containsExactlyInAnyOrder("버그글", "제안글");
    }
}
