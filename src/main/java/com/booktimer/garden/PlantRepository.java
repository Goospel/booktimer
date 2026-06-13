package com.booktimer.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 식물 카탈로그 조회 — 정적 시드(V35)라 읽기 전용.
 *
 * <p>해금이 임계 오름차순으로 순차적이라 그 순서로 정렬해 쓴다(임계 동률은 진열 순서로 보조 정렬).
 */
public interface PlantRepository extends JpaRepository<Plant, Long> {

    List<Plant> findAllByOrderByUnlockThresholdDaysAscDisplayOrderAsc();
}
