package com.booktimer.report;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Report 영속성. 저장 위주(관리자 조회는 추후) — 쌍 존재 검사와 탈퇴 정리 메서드만 둔다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterAndReported(User reporter, User reported);

    /** 회원 탈퇴 정리 — 내가 한 신고와 나를 향한 신고를 모두 제거한다. */
    void deleteByReporter(User reporter);

    void deleteByReported(User reported);
}
