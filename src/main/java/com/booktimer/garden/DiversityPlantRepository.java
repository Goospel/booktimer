package com.booktimer.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 작가·출판사 다양성 식물 카탈로그 조회 — 정적 시드(V38)라 읽기 전용.
 *
 * <p>도감은 진열 순서대로 보여주므로 {@code display_order} 오름차순으로 정렬해 쓴다(작가 묶음 → 출판사 묶음).
 */
public interface DiversityPlantRepository extends JpaRepository<DiversityPlant, Long> {

    List<DiversityPlant> findAllByOrderByDisplayOrderAsc();
}
