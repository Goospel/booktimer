package com.booktimer.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 장르 식물 카탈로그 조회 — 정적 시드(V36)라 읽기 전용.
 *
 * <p>도감은 진열 순서대로 보여주므로 {@code display_order} 오름차순으로 정렬해 쓴다(폴백 식물은 끝에 둔다).
 */
public interface GenrePlantRepository extends JpaRepository<GenrePlant, Long> {

    List<GenrePlant> findAllByOrderByDisplayOrderAsc();
}
