package com.booktimer.feedback;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 피드백/문의 유스케이스.
 *
 * <p>작성(저장)·본인 조회(스코핑)·관리자 조회/상태 표시/삭제를 담당한다. 상태 전이는 엔티티
 * ({@link Feedback#markRead()}/{@link Feedback#markResolved()})가 책임지고, 여기선 트랜잭션 안에서
 * id로 로드해 호출한다(dirty checking으로 flush). <b>IDOR 방어</b>: {@code deleteOwn}은 작성자 본인 글만
 * 지우고 남의 글 id면 조용히 무시한다(존재 누설 없음). 관리자 삭제는 작성자 무관(스팸 정리).
 */
@Service
@Transactional
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /** 문의를 접수한다(상태 SUBMITTED). 입력 검증은 {@link Feedback#of}가 담당. */
    public Feedback submit(User author, FeedbackType type, String title, String content) {
        return feedbackRepository.save(Feedback.of(author, type, title, content));
    }

    /** 작성자 본인 글만 — 최신순. */
    @Transactional(readOnly = true)
    public List<Feedback> myFeedback(User author) {
        return feedbackRepository.findByAuthorOrderByCreatedAtDesc(author);
    }

    /**
     * 관리자 조회 — 최신순. {@code filter}가 null이면 전체, 아니면 그 유형만. 트랜잭션 안에서 행 DTO로
     * 조립해 작성자(LAZY)를 미리 추출한다(뷰에서 detached 엔티티의 LAZY 연관을 안 건드림 — OSIV 비의존).
     */
    @Transactional(readOnly = true)
    public List<AdminFeedbackRow> feedbackRows(FeedbackType filter) {
        List<Feedback> list = (filter == null)
                ? feedbackRepository.findAllByOrderByCreatedAtDesc()
                : feedbackRepository.findByTypeOrderByCreatedAtDesc(filter);
        return list.stream().map(AdminFeedbackRow::from).toList();
    }

    /** 개발자: 읽음 표시(없는 id면 무시). */
    public void markRead(Long id) {
        feedbackRepository.findById(id).ifPresent(Feedback::markRead);
    }

    /** 개발자: 처리완료 표시(없는 id면 무시). */
    public void markResolved(Long id) {
        feedbackRepository.findById(id).ifPresent(Feedback::markResolved);
    }

    /** 개발자: 답장 — id로 로드해 답장을 달고 자동 읽음 처리(없는 id면 무시, 빈 답장은 거부). */
    public void reply(Long id, String text) {
        feedbackRepository.findById(id).ifPresent(f -> f.applyReply(text));
    }

    /**
     * 작성자 본인 글만 삭제 — 남의 글 id면 아무 일도 안 함(IDOR 방어, 존재 여부도 누설하지 않음).
     */
    public void deleteOwn(Long id, User author) {
        feedbackRepository.findById(id)
                .filter(f -> isOwnedBy(f, author))
                .ifPresent(feedbackRepository::delete);
    }

    /** 관리자 삭제 — 작성자 무관(스팸 정리). 없는 id면 무시. */
    public void deleteByAdmin(Long id) {
        feedbackRepository.findById(id).ifPresent(feedbackRepository::delete);
    }

    private boolean isOwnedBy(Feedback feedback, User author) {
        return author != null
                && feedback.getAuthor() != null
                && feedback.getAuthor().getId() != null
                && feedback.getAuthor().getId().equals(author.getId());
    }
}
