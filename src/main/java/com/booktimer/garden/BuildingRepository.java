package com.booktimer.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 출판사 건물 카탈로그 조회 — 정적 시드(V46) 읽기 전용. */
public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findAllByOrderByDisplayOrderAsc();
}
