package com.booktimer.garden;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 마을 꾸미기(배치) 유스케이스 — BUILDING 축 건물을 캔버스 자유 위치에 놓고 저장한다.
 *
 * <p>식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration)은 마을 컨셉 전환으로 제거됨.
 * DB 테이블(garden_placement)은 보존하되 BUILDING 축 행만 렌더에 사용.
 *
 * <p>고아 방어: ownedByKey 교집합(BUILDING만)이 1차 방어, layoutOf()의 명시 BUILDING 필터가 2차 방어.
 */
@Service
public class GardenLayoutService {

    /** 정원 월드 가로 픽셀(고정 종횡비 — 프론트 좌표 변환·카메라 핏 기준). */
    public static final int WORLD_WIDTH = 1000;
    /** 정원 월드 세로 픽셀(고정 종횡비 5:4). */
    public static final int WORLD_HEIGHT = 800;

    private final GardenPlacementRepository placementRepository;
    private final GardenService gardenService;

    public GardenLayoutService(GardenPlacementRepository placementRepository, GardenService gardenService) {
        this.placementRepository = placementRepository;
        this.gardenService = gardenService;
    }

    /**
     * 건물 배치를 원자적 교체 저장한다.
     */
    @Transactional
    public void saveLayout(User user, LayoutSaveRequest req) {
        save(user, req.plantsOrEmpty());
    }

    /**
     * 사용자의 배치를 BUILDING 축만, z 오름차순으로 내준다.
     * 보유를 잃은 건물(고아)은 ownedByKey 교집합 + 명시 BUILDING 필터로 제외.
     */
    @Transactional
    public List<PlacedItem> layoutItemsOf(User user) {
        List<PlacedItem> items = new ArrayList<>();
        for (PlacedPlant p : layoutOf(user)) {
            items.add(PlacedItem.plant(p));
        }
        items.sort(Comparator.comparingInt(PlacedItem::z));
        return items;
    }

    /**
     * 사용자의 저장된 배치를 현재 보유와 교집합해 렌더용으로 내준다.
     * <ul>
     *   <li>1차 방어: ownedByKey = ownedPlants() = BUILDING만 → 다른 축 고아 자동 탈락</li>
     *   <li>2차 방어: 명시 BUILDING 필터 → 고아 행 이중 방어(defense-in-depth)</li>
     * </ul>
     */
    @Transactional
    public List<PlacedPlant> layoutOf(User user) {
        Map<PlacementKey, OwnedPlant> ownedByKey = ownedByKey(user);
        List<PlacedPlant> placed = new ArrayList<>();
        for (GardenPlacement gp : placementRepository.findByUser(user)) {
            // 2차 방어: BUILDING 이외 축(TIME·GENRE·DIVERSITY·RECIPE·AUTHOR) 행 명시 차단
            if (gp.getAxis() != PlacementAxis.BUILDING) {
                continue;
            }
            OwnedPlant meta = ownedByKey.get(new PlacementKey(gp.getAxis(), gp.getPlantCode()));
            if (meta != null) { // 1차 방어: 교집합 — 보유 잃은 건물은 렌더에서 제외(유령 방지)
                placed.add(new PlacedPlant(gp.getAxis(), gp.getPlantCode(),
                        meta.emoji(), meta.name(), meta.spriteId(),
                        gp.getPosX(), gp.getPosY(), gp.getZOrder(), gp.getRotation(), gp.getScale()));
            }
        }
        placed.sort(Comparator.comparingInt(PlacedPlant::z));
        return placed;
    }

    /**
     * 사용자의 배치를 통째 교체 저장한다.
     * 저장 전 전수 검증: 미보유 건물(위조)·좌표 범위 밖(0~1)·같은 건물 중복·BUILDING 이외 축을 거부.
     */
    @Transactional
    public void save(User user, List<PlacementRequest> requests) {
        Map<PlacementKey, OwnedPlant> ownedByKey = ownedByKey(user);
        Set<PlacementKey> seenPlants = new HashSet<>();
        for (PlacementRequest r : requests) {
            if (r.axis() == null || r.code() == null) {
                throw new IllegalArgumentException("배치 요청에 축·식물 코드가 비어 있습니다.");
            }
            if (r.x() < 0 || r.x() > 1 || r.y() < 0 || r.y() > 1) {
                throw new IllegalArgumentException("정규화 범위(0~1)를 벗어난 좌표입니다: (" + r.x() + ", " + r.y() + ")");
            }
            if (r.rotation() < 0 || r.rotation() > 360) {
                throw new IllegalArgumentException("회전 범위(0~360)를 벗어났습니다: " + r.rotation());
            }
            if (r.scale() < 0.5 || r.scale() > 2.0) {
                throw new IllegalArgumentException("크기 범위(0.5~2.0)를 벗어났습니다: " + r.scale());
            }
            PlacementKey key = new PlacementKey(r.axis(), r.code());
            if (!ownedByKey.containsKey(key)) {
                throw new IllegalArgumentException("보유하지 않은 건물은 배치할 수 없습니다: " + r.axis() + "/" + r.code());
            }
            if (!seenPlants.add(key)) {
                throw new IllegalArgumentException("같은 건물을 두 번 배치할 수 없습니다: " + r.axis() + "/" + r.code());
            }
        }
        placementRepository.deleteByUser(user);
        placementRepository.flush();
        for (PlacementRequest r : requests) {
            placementRepository.save(GardenPlacement.of(user, r.axis(), r.code(), r.x(), r.y(), r.z(), r.rotation(), r.scale()));
        }
    }

    /** 현재 보유 건물을 (axis, code) → 메타 맵으로 — 보유 검증·메타 결합 공통 소스. */
    private Map<PlacementKey, OwnedPlant> ownedByKey(User user) {
        Map<PlacementKey, OwnedPlant> map = new HashMap<>();
        for (OwnedPlant p : gardenService.view(user).ownedPlants()) {
            map.put(new PlacementKey(p.axis(), p.code()), p);
        }
        return map;
    }

    private record PlacementKey(PlacementAxis axis, String code) {}
}
