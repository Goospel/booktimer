package com.booktimer.garden;

import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 한 사용자가 발견한 트랙 B 레시피 식물 한 건 (발견 이력 — 영구 저장).
 *
 * <p>트랙 A(시간축·장르축)는 보유를 저장하지 않고 독서 실적에서 <b>유도</b>한다(부채 모델 N-058). 트랙 B는
 * 반대로 발견을 <b>저장</b>한다(설계 §2.2) — 두 이유 때문이다: ① 발견은 "처음 만족된 순간"의 이벤트라
 * 그 순간을 박아야 "새 식물 발견!"·NEW가 가능하고, ② 책장이 바뀌면(읽고싶음 책을 옮기거나 삭제) 조합이
 * 깨지는데, 유도였다면 발견한 식물이 사라져 최악의 UX가 된다. 한 번 발견하면 영구 보유한다.
 *
 * <p>{@code (user, plant_code)} 유니크로 같은 식물의 중복 발견을 막는다(조회 시 평가가 매번 재발견을 시도해도
 * 멱등) — 동시성 안전망이자 {@code existsByUserAndPlantCode} 가드와 짝을 이룬다.
 */
@Entity
@Table(name = "user_discovered_plant", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_discovered_plant", columnNames = {"user_id", "plant_code"})
})
public class UserDiscoveredPlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 발견한 식물 코드 — {@link RecipePlant#getCode()}·{@link RecipeDefinition#plantCode()}와 일치. */
    @Column(name = "plant_code", nullable = false)
    private String plantCode;

    /** 발견 시각(주입 Clock 기준). */
    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    protected UserDiscoveredPlant() {
        // JPA
    }

    private UserDiscoveredPlant(User user, String plantCode, Instant discoveredAt) {
        this.user = user;
        this.plantCode = plantCode;
        this.discoveredAt = discoveredAt;
    }

    public static UserDiscoveredPlant of(User user, String plantCode, Instant discoveredAt) {
        return new UserDiscoveredPlant(user, plantCode, discoveredAt);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }
}
