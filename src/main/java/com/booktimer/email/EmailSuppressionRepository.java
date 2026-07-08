package com.booktimer.email;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 억제 목록 리포지토리 — 발송 전 존재 검사({@link #existsByEmail})가 핵심 경로다.
 * 조회 값은 항상 정규화(trim+소문자)된 이메일이어야 한다({@link EmailSuppressionService#normalize}).
 */
public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, Long> {

    boolean existsByEmail(String email);
}
