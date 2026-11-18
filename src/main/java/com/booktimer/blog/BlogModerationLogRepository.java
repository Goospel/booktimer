package com.booktimer.blog;

import org.springframework.data.jpa.repository.JpaRepository;

/** 처리 기록 — {@code save}만 쓴다(읽기는 시험 · 운영 SQL). */
public interface BlogModerationLogRepository extends JpaRepository<BlogModerationLog, Long> {
}
