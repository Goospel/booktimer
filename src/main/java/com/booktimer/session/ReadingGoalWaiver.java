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
 * <p>제약은 {@code (user, waived_date)} 유니크 하나다 — 같은 날을 두 번 지우는 건 무의미하고, 동시 요청
 * race도 이 제약이 잡는다. 하루 1회 상한이던 {@code (user, granted_on)} 유니크는 2026-08-14에 제거했다
 * (V67): 부채 7일 자동 소멸을 폐지해 빚이 계속 누적되므로 갚을 수단도 계속 열려 있어야 한다. 어뷰징
 * 상한은 이제 <b>부채 자체</b>가 든다 — 지울 과거 날이 없으면 거부되고, 오늘 몫은 애초에 대상이 아니다.
 * {@code granted_on} 컬럼은 "언제 지급됐나"를 남기는 기록으로 유지한다(유니크만 빠졌다).
 */
@Entity
@Table(name = "reading_goal_waiver", uniqueConstraints = {
        @UniqueConstraint(name = "uk_goal_waiver_date", columnNames = {"user_id", "waived_date"})
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

    /** 용서권이 지급된 날(유저 타임존 오늘) — 지급 시점 기록(옛 일일 상한의 키였다). */
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
