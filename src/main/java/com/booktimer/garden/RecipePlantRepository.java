package com.booktimer.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 트랙 B 레시피 결과 식물 카탈로그 조회 — 정적 시드(V37)라 읽기 전용.
 *
 * <p>도감은 진열 순서대로 보여주므로 {@code display_order} 오름차순으로 정렬해 쓴다.
 */
public interface RecipePlantRepository extends JpaRepository<RecipePlant, Long> {

    List<RecipePlant> findAllByOrderByDisplayOrderAsc();
}
