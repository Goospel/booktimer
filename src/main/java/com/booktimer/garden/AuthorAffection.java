package com.booktimer.garden;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 작가 캐릭터 먹이주기 정(affection) 영속성 — 먹이 루프 SPEND 측.
 *
 * <p>사용자가 배회 작가 캐릭터에게 먹이를 줄 때마다 해당 캐릭터의 {@code feedCount}를 누적한다.
 * {@code (user, characterCode)} 유니크 — 사용자 × 캐릭터 행 1개(upsert 기반).
 * EARN(달성일→먹이 획득)은 저장하지 않고 유도({@link DailyQuotaCalculator}).
 */
@Entity
@Table(name = "author_affection",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_author_affection_user_code",
                columnNames = {"user_id", "character_code"}))
public class AuthorAffection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "character_code", nullable = false, length = 50)
    private String characterCode;

    /** 누적 먹인 횟수. 최초 먹이기에서 1로 시작하고, 이후 먹일 때마다 1씩 증가한다. */
    @Column(name = "feed_count", nullable = false)
    private int feedCount;

    protected AuthorAffection() {
        // JPA
    }

    /**
     * 최초 먹이기 행을 만든다 — {@code feedCount=1}로 시작.
     * 이후 추가 먹이기는 {@link #incrementFeedCount()}으로.
     */
    public static AuthorAffection create(User user, String characterCode) {
        AuthorAffection a = new AuthorAffection();
        a.user = user;
        a.characterCode = characterCode;
        a.feedCount = 1;
        return a;
    }

    /** 먹이기 1회 추가 — 기존 행에만 호출. */
    public void incrementFeedCount() {
        this.feedCount++;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getCharacterCode() { return characterCode; }
    public int getFeedCount() { return feedCount; }
}
