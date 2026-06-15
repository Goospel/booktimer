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
 * 정원 꾸미기(배치) 유스케이스 — 보유 식물을 캔버스 자유 위치에 놓고 저장한다.
 *
 * <p>도감 보유는 독서 실적에서 유도/저장하지만(부채 모델 N-058·트랙 B 발견), "어디에 놓을지"는 사용자 의도라
 * 별도로 저장한다({@link GardenPlacement}). 보유 집합은 {@link GardenService#view}가 내는 {@link GardenView#ownedPlants()}를
 * 재사용한다 — 새로 계산하지 않고 도감과 같은 한 소스를 본다(설계 §3).
 *
 * <p>자유 위치 전환(Phase 1): 격자 셀 번호 대신 정규화 좌표(0~1)+zOrder로 저장한다. 같은 좌표 겹침을 허용하고,
 * 식물 위치는 월드 픽셀 크기 무관이라 반응형에 자연 정합한다(설계 §2.3).
 *
 * <p><b>유령 방지</b>: 저장된 배치라도 그 식물을 현재 보유하지 않으면(예: 완독책 삭제로 장르 식물 상실)
 * {@link #layoutOf}가 현재 보유와 교집합해 렌더에서 뺀다(설계 §5 리스크 2).
 *
 * <p><b>위조 방어</b>: {@link #save}는 미보유 식물·좌표 범위 밖·같은 식물 중복 요청을 거부한다(설계 §4).
 */
@Service
public class GardenLayoutService {

    /** 정원 월드 가로 픽셀(고정 종횡비 — 프론트 좌표 변환·카메라 핏 기준, 설계 §2.3). */
    public static final int WORLD_WIDTH = 1000;
    /** 정원 월드 세로 픽셀(고정 종횡비). */
    public static final int WORLD_HEIGHT = 640;

    private final GardenPlacementRepository placementRepository;
    private final GardenService gardenService;

    public GardenLayoutService(GardenPlacementRepository placementRepository, GardenService gardenService) {
        this.placementRepository = placementRepository;
        this.gardenService = gardenService;
    }

    /**
     * 사용자의 저장된 배치를 현재 보유와 <b>교집합</b>해 렌더용으로 내준다 — 보유를 잃은 식물(유령)은 뺀다(설계 §5 리스크 2).
     * 식물 메타(이모지·이름)는 보유 식물 풀에서 결합하고, 결과는 zOrder 오름차순(뒤→앞 렌더순)으로 돌려준다.
     *
     * <p>{@link GardenService#view}가 트랙 B 발견을 저장(쓰기)할 수 있어 읽기-쓰기 트랜잭션이다(멱등).
     */
    @Transactional
    public List<PlacedPlant> layoutOf(User user) {
        Map<PlacementKey, OwnedPlant> ownedByKey = ownedByKey(user);
        List<PlacedPlant> placed = new ArrayList<>();
        for (GardenPlacement gp : placementRepository.findByUser(user)) {
            OwnedPlant meta = ownedByKey.get(new PlacementKey(gp.getAxis(), gp.getPlantCode()));
            if (meta != null) { // 교집합 — 보유 잃은 식물은 렌더에서 제외(유령 방지)
                placed.add(new PlacedPlant(gp.getAxis(), gp.getPlantCode(),
                        meta.emoji(), meta.name(), meta.spriteId(), // spriteId는 보유 메타에서 결합(A2)
                        gp.getPosX(), gp.getPosY(), gp.getZOrder()));
            }
        }
        placed.sort(Comparator.comparingInt(PlacedPlant::z)); // zOrder 오름차순 = 뒤→앞 렌더
        return placed;
    }

    /**
     * 사용자의 배치를 통째 교체 저장한다 — 본인 범위를 비우고 새 배치를 넣는다(설계 §2.4).
     * 저장 전 전수 검증: 미보유 식물(위조)·좌표 범위 밖(0~1)·같은 식물 중복을 거부한다(설계 §4). 검증 실패 시 저장 전체가 무산된다.
     * 자유 위치라 같은 좌표 겹침은 허용한다(셀 중복 거부 없음).
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
            PlacementKey key = new PlacementKey(r.axis(), r.code());
            if (!ownedByKey.containsKey(key)) {
                throw new IllegalArgumentException("보유하지 않은 식물은 배치할 수 없습니다: " + r.axis() + "/" + r.code());
            }
            if (!seenPlants.add(key)) {
                throw new IllegalArgumentException("같은 식물을 두 번 배치할 수 없습니다: " + r.axis() + "/" + r.code());
            }
        }
        // 교체 저장: 본인 범위를 비우고(즉시 실행되는 bulk delete) 새 배치를 삽입한다.
        placementRepository.deleteByUser(user);
        placementRepository.flush();
        for (PlacementRequest r : requests) {
            placementRepository.save(GardenPlacement.of(user, r.axis(), r.code(), r.x(), r.y(), r.z()));
        }
    }

    /** 현재 보유 식물을 (axis, code) → 메타 맵으로 — 보유 검증·메타 결합 공통 소스(설계 §3). */
    private Map<PlacementKey, OwnedPlant> ownedByKey(User user) {
        Map<PlacementKey, OwnedPlant> map = new HashMap<>();
        for (OwnedPlant p : gardenService.view(user).ownedPlants()) {
            map.put(new PlacementKey(p.axis(), p.code()), p);
        }
        return map;
    }

    /** 배치 식별 키 — code가 축 간 비유니크라 (axis, code) 복합이다(설계 §1). */
    private record PlacementKey(PlacementAxis axis, String code) {
    }
}
