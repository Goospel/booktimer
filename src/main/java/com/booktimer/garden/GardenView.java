package com.booktimer.garden;

import java.util.ArrayList;
import java.util.List;

/**
 * 독서 정원 화면(베타) 뷰 모델 — 대시보드 잔디 카드 안 접힌 패널에 싣는다.
 *
 * <p>보유를 저장하지 않고 유도하므로(부채 모델 N-058) 매 조회 시 독서 실적에서 새로 계산된다.
 *
 * @param plants          시간축 카탈로그 전체(임계 오름차순, 보유·미보유 모두) — 도감 그리드 렌더용
 * @param ownedCount      시간축 보유 종 수
 * @param totalCount      시간축 전체 종 수
 * @param achievedDays    누적 목표 달성일 수(진척·다음 해금 계산 입력)
 * @param daysToNextUnlock 다음 식물까지 남은 달성일. 전부 보유면 null
 * @param nextPlantName   다음에 해금될 식물 이름. 전부 보유면 null
 * @param genrePlants     장르축 카탈로그 전체(진열 순서, 보유·미보유 모두) — 수집 도감 렌더용
 * @param ownedGenreCount 장르축 보유 종 수
 * @param totalGenreCount 장르축 전체 종 수
 * @param diversityPlants 다양성축(작가·출판사) 카탈로그 전체(진열 순서, 보유·미보유 모두). 식물의 {@code kind}로
 *                        작가/출판사 그리드를 가른다 — 수집 도감 렌더용
 * @param ownedAuthorCount    작가 식물 보유 종 수
 * @param totalAuthorCount    작가 식물 전체 종 수
 * @param ownedPublisherCount 출판사 식물 보유 종 수
 * @param totalPublisherCount 출판사 식물 전체 종 수
 * @param recipePlants    트랙 B 레시피 결과 식물 카탈로그 전체(진열 순서). 미발견은 "???" 슬롯으로 렌더
 * @param mysterySlotCount 미발견 레시피 수(존재·개수는 알리되 조건은 숨김 — 호기심 유도, §2.5)
 * @param justDiscovered  이번 조회에서 새로 발견된 식물 이름들(토스트용, 진열 순서). 없으면 빈 리스트
 */
public record GardenView(List<PlantState> plants,
                         int ownedCount,
                         int totalCount,
                         int achievedDays,
                         Integer daysToNextUnlock,
                         String nextPlantName,
                         List<GenrePlantState> genrePlants,
                         int ownedGenreCount,
                         int totalGenreCount,
                         List<DiversityPlantState> diversityPlants,
                         int ownedAuthorCount,
                         int totalAuthorCount,
                         int ownedPublisherCount,
                         int totalPublisherCount,
                         List<DiscoveredPlantState> recipePlants,
                         int mysterySlotCount,
                         List<String> justDiscovered) {

    /**
     * 네 수집축의 <b>보유</b> 식물을 배치용 단일 표현({@link OwnedPlant})으로 평탄화해 모은다.
     *
     * <p>꾸미기(배치)는 보유 식물 전부를 대상으로 하므로(설계 §2.1) 축별 메타가 제각각인 상태 리스트를
     * {@code (axis, code, emoji, name)}로 통일한다. 시간축은 {@code owned}, 장르·다양성축도 {@code owned},
     * 레시피축은 {@code discovered}가 보유 기준이다. 이 결과가 팔레트 풀이자 {@code GardenLayoutService.save}의
     * 보유 검증 집합으로 쓰여 미보유 식물 배치(위조)를 막는다(설계 §4).
     */
    public List<OwnedPlant> ownedPlants() {
        List<OwnedPlant> owned = new ArrayList<>();
        for (PlantState s : plants) {
            if (s.owned()) {
                // 시간축만 A2 SVG 스프라이트 보유 — null이면 이모지 폴백(미적용 종은 정상 null).
                owned.add(new OwnedPlant(PlacementAxis.TIME, s.plant().getCode(),
                        s.plant().getEmoji(), s.plant().getName(), s.plant().getSpriteId()));
            }
        }
        for (GenrePlantState s : genrePlants) {
            if (s.owned()) {
                owned.add(new OwnedPlant(PlacementAxis.GENRE, s.genrePlant().getCode(),
                        s.genrePlant().getEmoji(), s.genrePlant().getName(), null));
            }
        }
        for (DiversityPlantState s : diversityPlants) {
            if (s.owned()) {
                owned.add(new OwnedPlant(PlacementAxis.DIVERSITY, s.plant().getCode(),
                        s.plant().getEmoji(), s.plant().getName(), null));
            }
        }
        for (DiscoveredPlantState s : recipePlants) {
            if (s.discovered()) {
                owned.add(new OwnedPlant(PlacementAxis.RECIPE, s.recipePlant().getCode(),
                        s.recipePlant().getEmoji(), s.recipePlant().getName(), null));
            }
        }
        return owned;
    }
}
