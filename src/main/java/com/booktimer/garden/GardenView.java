package com.booktimer.garden;

import java.util.List;

/**
 * 독서 정원 화면(베타) 뷰 모델 — 대시보드 잔디 카드 안 접힌 패널에 싣는다.
 *
 * <p>보유를 저장하지 않고 유도하므로(부채 모델 N-058) 매 조회 시 독서 실적에서 새로 계산된다.
 *
 * @param plants          카탈로그 전체(임계 오름차순, 보유·미보유 모두) — 도감 그리드 렌더용
 * @param ownedCount      보유 종 수
 * @param totalCount      전체 종 수
 * @param achievedDays    누적 목표 달성일 수(진척·다음 해금 계산 입력)
 * @param daysToNextUnlock 다음 식물까지 남은 달성일. 전부 보유면 null
 * @param nextPlantName   다음에 해금될 식물 이름. 전부 보유면 null
 */
public record GardenView(List<PlantState> plants,
                         int ownedCount,
                         int totalCount,
                         int achievedDays,
                         Integer daysToNextUnlock,
                         String nextPlantName) {
}
