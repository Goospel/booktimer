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

/**
 * 한 사용자가 정원 캔버스의 한 자유 위치에 놓은 소품 한 건 (꾸미기 배치 — 영구 저장, Phase 3).
 *
 * <p>식물 배치({@link GardenPlacement})와 두 가지가 근본적으로 다르다(설계 §1):
 * <ul>
 *   <li><b>중복 허용</b> — 같은 소품(울타리·디딤돌)을 여러 개 놓는다. 그래서 {@code uk(user, code)} 같은
 *       유니크가 <b>없다</b> — 각 행이 독립 인스턴스다(식물의 "한 식물 한 번"과 반대).</li>
 *   <li><b>보유 무관</b> — 소품은 해금 대상이 아니라 카탈로그 존재만 검증한다. 보유 교집합(유령 방지) 대신
 *       카탈로그 교집합으로 고아(삭제된 카탈로그 코드)만 렌더에서 뺀다({@code GardenLayoutService.layoutItemsOf}).</li>
 * </ul>
 *
 * <p>좌표(정규화 0~1)·변형(rotation·scale)·겹침 순서({@link #zOrder})는 식물 배치와 같은 모델·단위다.
 * 단 {@code zOrder}는 식물과 <b>통합 단일 스케일</b>이라(연못은 식물 뒤·벤치는 식물 앞) 조회 시 식물+소품을 z로
 * 병합 정렬한다(설계 §2 결정 ③). 저장은 {@code GardenLayoutService.saveDecorations}가 유저 범위 전체를 교체(delete+insert)한다.
 */
@Entity
@Table(name = "garden_decoration_placement")
public class GardenDecorationPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 배치한 소품 코드 — {@link Decoration#getCode()}와 일치. 소품은 축이 없어 code 단독으로 식별한다(식물의 (axis,code)와 대비). */
    @Column(name = "decoration_code", nullable = false)
    private String decorationCode;

    /** 가로 위치(정규화 0~1, 월드 폭 비율). */
    @Column(name = "pos_x", nullable = false)
    private double posX;

    /** 세로 위치(정규화 0~1, 월드 높이 비율). */
    @Column(name = "pos_y", nullable = false)
    private double posY;

    /** 겹침 앞뒤 순서(zOrder, 클수록 앞) — 식물과 통합 단일 스케일. */
    @Column(name = "z_order", nullable = false)
    private int zOrder;

    /** 사용자 회전(도, 0~360). 기본 0(무변형). */
    @Column(nullable = false)
    private double rotation;

    /** 사용자 크기 배율(0.5~2.0). 기본 1(무변형). */
    @Column(nullable = false)
    private double scale;

    protected GardenDecorationPlacement() {
        // JPA
    }

    private GardenDecorationPlacement(User user, String decorationCode, double posX, double posY, int zOrder,
                                      double rotation, double scale) {
        this.user = user;
        this.decorationCode = decorationCode;
        this.posX = posX;
        this.posY = posY;
        this.zOrder = zOrder;
        this.rotation = rotation;
        this.scale = scale;
    }

    public static GardenDecorationPlacement of(User user, String decorationCode,
                                               double posX, double posY, int zOrder, double rotation, double scale) {
        return new GardenDecorationPlacement(user, decorationCode, posX, posY, zOrder, rotation, scale);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getDecorationCode() {
        return decorationCode;
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

    public double getRotation() {
        return rotation;
    }

    public double getScale() {
        return scale;
    }
}
