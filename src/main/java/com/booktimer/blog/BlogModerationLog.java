package com.booktimer.blog;

import com.booktimer.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 처리 기록 한 줄(B2b · V109 — 처리방침 5절 「그 처리 기록은 처리가 끝난 뒤 3년」). <b>쓰기만 한다</b>(고치는 메서드 0) · users FK 없음 ·
 * 탈퇴(purge)가 건드리지 않는다 — 가린 상태(열 셋)는 다시 보이기 · 글쓴이 지우기 · 탈퇴로 사라져도 이 줄은 남는다.
 */
@Entity
@Table(name = "blog_moderation_log")
public class BlogModerationLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** POST · COMMENT · QUOTE · USER. */
    @Column(nullable = false, length = 8)
    private String unit;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    /** TEMP · COPYRIGHT · POLICY · RESTORE · REMOVE · NONE · RESTRICT · UNRESTRICT. */
    @Column(nullable = false, length = 12)
    private String action;

    @Column(name = "report_id")
    private Long reportId;

    @Column(length = Moderation.NOTE_MAX)
    private String note;

    protected BlogModerationLog() {
    }

    static BlogModerationLog of(String unit, long unitId, String action, Long reportId, String note) {
        BlogModerationLog l = new BlogModerationLog();
        l.unit = unit;
        l.unitId = unitId;
        l.action = action;
        l.reportId = reportId;
        l.note = note;
        return l;
    }

    public Long getId() {
        return id;
    }

    public String getUnit() {
        return unit;
    }

    public Long getUnitId() {
        return unitId;
    }

    public String getAction() {
        return action;
    }

    public Long getReportId() {
        return reportId;
    }

    public String getNote() {
        return note;
    }
}
