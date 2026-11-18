package com.booktimer.blog;

import com.booktimer.blog.BlogReport.Status;
import com.booktimer.blog.BlogReport.Target;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlogReportRepository extends JpaRepository<BlogReport, Long> {

    boolean existsByReporterAndTargetTypeAndTargetId(User reporter, Target targetType, Long targetId);

    /** 내 신고(REQ-335) — 최근 것부터 200개(ponytail: 넘으면 페이지를 붙인다). */
    List<BlogReport> findTop200ByReporterOrderByCreatedAtDescIdDesc(User reporter);

    List<BlogReport> findByTargetTypeAndTargetIdAndStatus(Target targetType, Long targetId, Status status);

    long countByStatus(Status status);

    /** 관리자 신고함 — 열린 것 먼저(OPEN &lt; RESOLVED) · 최근 순 100개(ponytail: 넘으면 페이지). */
    List<BlogReport> findTop100ByOrderByStatusAscCreatedAtDescIdDesc();

    /** 탈퇴 정리 — 내가 한 신고(blog_report.reporter_id FK · 처리방침 5절 「신고 내역은 신고자 탈퇴까지」). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlogReport r where r.reporter = :user")
    void deleteByReporter(@Param("user") User user);

    /** 탈퇴 정리 — 남이 내 글 · 댓글을 신고한 행은 남기고 대상 글쓴이 id만 비운다(FK 없음 · 색인 idx_blog_report_target_user). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BlogReport r set r.targetUserId = null where r.targetUserId = :id")
    void clearTargetUser(@Param("id") Long userId);
}
