package com.booktimer.study;

import com.booktimer.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * AI 호출의 <b>전역</b> 하루 상한 카운터 한 줄 — 「이 날 서비스 전체가 몇 번 불렀나」.
 *
 * <p>{@link StudyAiUsage}에서 {@code user}만 뺀 모양이고, 규율도 같다: 로그가 아니라 <b>가감 가능한
 * 카운터</b>이고(외부 호출 실패 시 되돌려야 한다), UNIQUE를 <b>엔티티에도</b> 선언한다(메인 테스트는
 * Hibernate가 H2 스키마를 만들어 마이그레이션의 제약을 모른다 — 여기 없으면 경합 테스트가 「INSERT
 * 두 개가 다 성공하는」 세계에서 돌아 실제 배치를 재지 못한다).
 *
 * <p><b>왜 이게 따로 필요한가</b>: 사용자당 상한만으로는 총액에 천장이 없다. 비용이 계정 수에 선형으로
 * 늘고, 계정 수는 공격자가 정한다(가입 레이트리밋 없음). 관리자 계정이 뚫려 전원이 승인되는
 * 시나리오에서 마지막으로 남는 방어선이 이것이다(보안 리뷰 2026-09-08 S-1).
 *
 * <p>⚠️ <b>{@code usageDate}는 UTC 달력일이다.</b> {@link StudyAiUsage}는 {@link StudyDates#today}
 * (유저 타임존)로 키를 잡는데, 타임존은 사용자가 설정에서 제한 없이 바꿀 수 있어 같은 순간에 로컬 날짜가
 * 둘이 된다. 전역 상한이 그 키를 쓰면 자정 근처에 타임존만 바꿔 그 날 몫을 두 배로 쓸 수 있다 —
 * <b>막으려는 것에 같은 구멍이 뚫린다.</b> 이 값은 사용자에게 보이지 않는 내부 키라 UTC로 둔다.
 */
@Entity
@Table(name = "study_ai_daily_total",
        uniqueConstraints = @UniqueConstraint(name = "uq_study_ai_daily_total",
                columnNames = {"usage_date"}))
public class StudyAiDailyTotal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** <b>UTC</b> 달력일 — 위 클래스 javadoc의 경고 참조. */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private int used;

    protected StudyAiDailyTotal() {
        // JPA
    }

    private StudyAiDailyTotal(LocalDate usageDate) {
        this.usageDate = usageDate;
        this.used = 0;
    }

    /** 아직 아무것도 안 쓴 새 카운터 — 증가는 조건부 UPDATE(원자적)가 맡는다. */
    public static StudyAiDailyTotal of(LocalDate usageDate) {
        return new StudyAiDailyTotal(usageDate);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public int getUsed() {
        return used;
    }
}
