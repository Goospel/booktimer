package com.booktimer.session;

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

import java.time.LocalDate;

/**
 * 밀린 하루 용서권 한 행 — "이 사용자의 {@code waivedDate} 부채를 {@code grantedOn}에 용서했다".
 *
 * <p>리워드 광고 1회 시청의 보상이다. 부채는 저장하지 않고 완료 세션에서 매번 유도하므로
 * ({@link WeeklyDebtCalculator}) 보상 지급은 "용서한 날짜" 마킹 하나로 끝난다 — 웹 대시보드와 미니앱이
 * 같은 계산 경로({@link ReadingDebtService})를 쓰는 덕에 두 채널 표시가 자동으로 함께 줄어든다.
 *
 * <p>용서는 <b>부채 표시에만</b> 작용한다. 잔디·스트릭·먹이(={@code metDayCount})는 전부 독서 세션에서
 * 따로 유도되므로 이 행이 생겨도 불변이다 — 광고로 재화를 파밍할 수 없다는 뜻이고, 그게 이 설계가
 * 먹이 지급 대신 부채 용서를 보상으로 고른 이유다.
 *
 * <p>제약 두 개가 어뷰징 상한을 든다(SDK에 서버사이드 보상 검증이 없어 지급 요청은 클라 주장일 뿐이므로,
 * 신뢰가 아니라 상한으로 캡한다): {@code (user, granted_on)} 유니크 = 하루 1회,
 * {@code (user, waived_date)} 유니크 = 같은 날 중복 용서 불가. 동시 요청 race도 제약이 잡는다.
 */
@Entity
@Table(name = "reading_goal_waiver", uniqueConstraints = {
        @UniqueConstraint(name = "uk_goal_waiver_date", columnNames = {"user_id", "waived_date"}),
        @UniqueConstraint(name = "uk_goal_waiver_grant", columnNames = {"user_id", "granted_on"})
})
public class ReadingGoalWaiver extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 용서권을 받은 사용자. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 부채가 용서된 날(유저 타임존 일자) — 과거의 빠뜨린 날. */
    @Column(name = "waived_date", nullable = false)
    private LocalDate waivedDate;

    /** 용서권이 지급된 날(유저 타임존 오늘) — 일일 1회 상한의 키. */
    @Column(name = "granted_on", nullable = false)
    private LocalDate grantedOn;

    protected ReadingGoalWaiver() {
        // JPA
    }

    private ReadingGoalWaiver(User user, LocalDate waivedDate, LocalDate grantedOn) {
        if (user == null || waivedDate == null || grantedOn == null) {
            throw new IllegalArgumentException("user/waivedDate/grantedOn must not be null");
        }
        this.user = user;
        this.waivedDate = waivedDate;
        this.grantedOn = grantedOn;
    }

    /**
     * 용서권 한 행을 만든다. 생성 후 불변 — 수정 API를 두지 않는다(지급 기록이라 정정 개념이 없다).
     *
     * @throws IllegalArgumentException 인자가 null인 경우
     */
    public static ReadingGoalWaiver create(User user, LocalDate waivedDate, LocalDate grantedOn) {
        return new ReadingGoalWaiver(user, waivedDate, grantedOn);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getWaivedDate() {
        return waivedDate;
    }

    public LocalDate getGrantedOn() {
        return grantedOn;
    }
}
