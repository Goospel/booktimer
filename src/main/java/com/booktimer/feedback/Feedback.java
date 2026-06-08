package com.booktimer.feedback;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 사용자 피드백/문의 — {@code author}(일반 사용자)가 개발자에게 보내는 한 건.
 *
 * <p>유형(버그/제안/기타)·제목·내용을 담고, 처리 {@link FeedbackStatus}는 SUBMITTED(미확인)에서 시작한다.
 * 개발자(ADMIN)가 {@link #markRead()}/{@link #markResolved()}로 진행시키며, 그 상태는 <b>작성자 본인만</b>
 * 조회한다(노출 스코핑은 서비스/컨트롤러가 담당). report 엔티티와 같은 구조(BaseTimeEntity·STRING enum·LAZY FK).
 */
@Entity
@Table(name = "feedback")
public class Feedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작성자(일반 사용자). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status;

    protected Feedback() {
        // JPA
    }

    private Feedback(User author, FeedbackType type, String title, String content) {
        this.author = author;
        this.type = type;
        this.title = title;
        this.content = content;
        this.status = FeedbackStatus.SUBMITTED;
    }

    /**
     * 문의를 만든다. 작성자·유형은 필수, 제목·내용은 비어 있을 수 없다(앞뒤 공백 제거 후 저장).
     *
     * @throws IllegalArgumentException author/type이 null이거나 title/content가 비어 있는 경우
     */
    public static Feedback of(User author, FeedbackType type, String title, String content) {
        if (author == null || type == null) {
            throw new IllegalArgumentException("author/type must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return new Feedback(author, type, title.strip(), content.strip());
    }

    /** 개발자가 읽음 표시 — 미확인일 때만 읽음으로. 이미 처리완료면 그대로 둔다(되돌리지 않음). */
    public void markRead() {
        if (this.status == FeedbackStatus.SUBMITTED) {
            this.status = FeedbackStatus.READ;
        }
    }

    /** 개발자가 처리완료 표시 — 읽음을 건너뛰고 바로 처리완료도 가능(처리는 곧 확인을 함의). */
    public void markResolved() {
        this.status = FeedbackStatus.RESOLVED;
    }

    public Long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public FeedbackType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public FeedbackStatus getStatus() {
        return status;
    }
}
