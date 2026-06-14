package com.booktimer.garden;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 정원 배치 조회·저장.
 *
 * <p>{@link #findByUser}로 한 사용자의 전체 배치를 모아 렌더(현재 보유와 교집합)하고, {@link #deleteByUser}로
 * 저장 시 유저 범위를 통째로 비운 뒤 새 배치를 삽입한다(교체 저장 — 부분 수정·중복 방지가 행별로 깔끔, 설계 §2.4).
 * 두 조회·삭제 모두 {@code user} 범위라 IDOR(남의 배치 조작)이 구조적으로 막힌다.
 */
public interface GardenPlacementRepository extends JpaRepository<GardenPlacement, Long> {

    List<GardenPlacement> findByUser(User user);

    void deleteByUser(User user);
}
