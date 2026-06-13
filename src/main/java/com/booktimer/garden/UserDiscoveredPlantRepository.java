package com.booktimer.garden;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 트랙 B 발견 이력 조회·저장.
 *
 * <p>{@link #findByUser}로 한 사용자의 발견 집합을 모아 도감 보유를 칠하고, {@link #existsByUserAndPlantCode}로
 * 이미 발견한 레시피의 재저장을 막는다(멱등 — uk 제약과 짝).
 */
public interface UserDiscoveredPlantRepository extends JpaRepository<UserDiscoveredPlant, Long> {

    List<UserDiscoveredPlant> findByUser(User user);

    boolean existsByUserAndPlantCode(User user, String plantCode);
}
