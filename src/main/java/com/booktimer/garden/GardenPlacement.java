package com.booktimer.garden;

import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 한 사용자가 정원 캔버스의 한 자유 위치에 놓은 식물 한 건 (꾸미기 배치 — 영구 저장).
 *
 * <p>도감(시간·장르·다양성·레시피축)은 보유를 <b>유도</b>(시간·장르·다양성)하거나 발견을 <b>저장</b>(레시피)하지만,
 * 이 배치는 "어디에 놓을지"라는 순수 사용자 의도라 별도로 저장한다 — 도감 보유와 생명주기가 다르다(설계 §1·§5 리스크 2).
 * 보유를 잃으면(예: 완독책 삭제로 장르 식물이 사라짐) 이 행은 남아도 {@code GardenLayoutService.layoutOf}가
 * 현재 보유와 교집합해 렌더에서 빼므로 유령 식물이 화면에 새지 않는다.
 *
 * <p>자유 위치 전환(Phase 1)으로 격자 셀 번호({@code cell_index}) 대신 <b>정규화 좌표</b>({@link #posX}/{@link #posY},
 * 0~1)와 겹침 순서({@link #zOrder})를 저장한다. 정규화라 월드 픽셀 크기가 바뀌어도 데이터가 불변이고 검증이 단순하다
 * (0 ≤ v ≤ 1, 설계 §2.3). 겹침을 허용하므로 한 좌표에 둘 이상 놓일 수 있어 셀 유니크는 없앴다.
 *
 * <p>한 유니크로 무결성을 박는다:
 * <ul>
 *   <li>{@code uk(user_id, axis, plant_code)} — 한 식물은 한 번만 배치(같은 식물을 두 곳에 둘 수 없다).</li>
 * </ul>
 *
 * <p>식물 {@code code}가 축 간 비유니크라 {@link #axis}+{@link #plantCode} 복합으로 식물을 식별한다(설계 §1).
 * 저장은 {@code GardenLayoutService.save}가 유저 범위 전체를 교체(delete+insert)한다.
 */
@Entity
@Table(name = "garden_placement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_garden_placement_plant", columnNames = {"user_id", "axis", "plant_code"})
})
public class GardenPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 배치한 식물이 속한 수집축 — code가 축 간 비유니크라 식별의 절반(설계 §1). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlacementAxis axis;

    /** 배치한 식물 코드 — 그 축 카탈로그의 code와 일치. axis와 함께 식물을 식별한다. */
    @Column(name = "plant_code", nullable = false)
    private String plantCode;

    /** 가로 위치(정규화 0~1, 월드 폭 비율) — 자유 위치 전환(Phase 1). */
    @Column(name = "pos_x", nullable = false)
    private double posX;

    /** 세로 위치(정규화 0~1, 월드 높이 비율). */
    @Column(name = "pos_y", nullable = false)
    private double posY;

    /** 겹침 앞뒤 순서(zOrder, 클수록 앞) — 자유 위치라 식물이 겹칠 수 있어 렌더 순서를 박는다. */
    @Column(name = "z_order", nullable = false)
    private int zOrder;

    protected GardenPlacement() {
        // JPA
    }

    private GardenPlacement(User user, PlacementAxis axis, String plantCode, double posX, double posY, int zOrder) {
        this.user = user;
        this.axis = axis;
        this.plantCode = plantCode;
        this.posX = posX;
        this.posY = posY;
        this.zOrder = zOrder;
    }

    public static GardenPlacement of(User user, PlacementAxis axis, String plantCode,
                                     double posX, double posY, int zOrder) {
        return new GardenPlacement(user, axis, plantCode, posX, posY, zOrder);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public PlacementAxis getAxis() {
        return axis;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public double getPosX() {
        return posX;
    }

    public double getPosY() {
        return posY;
    }

    public int getZOrder() {
        return zOrder;
    }
}
