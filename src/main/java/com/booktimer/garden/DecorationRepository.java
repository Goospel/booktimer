package com.booktimer.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 소품 카탈로그 조회 — 정적 시드(V44)라 읽기 전용.
 *
 * <p>팔레트·렌더 모두 진열 순서로 쓰므로 {@code display_order} 오름차순으로 정렬해 낸다.
 */
public interface DecorationRepository extends JpaRepository<Decoration, Long> {

    List<Decoration> findAllByOrderByDisplayOrderAsc();
}
