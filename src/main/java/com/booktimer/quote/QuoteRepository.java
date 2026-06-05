package com.booktimer.quote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 격언 저장소. 목록은 최신 등록(id 내림차순 — auto_increment라 createdAt 동률 흔들림 없이 안정적) 먼저.
 */
public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findAllByOrderByIdDesc();
}
