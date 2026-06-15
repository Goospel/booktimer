package com.booktimer.garden;

/**
 * 팔레트에 놓을 수 있는 소품 한 종 (팔레트 렌더용 — 카탈로그 평탄화).
 *
 * <p>소품은 보유 무관이라 카탈로그 전체가 곧 팔레트다(식물은 {@link OwnedPlant} = 보유분만). 부트스트랩 JSON으로
 * 내려가 편집 팔레트가 렌더하고, 클릭하면 새 인스턴스가 캔버스에 추가된다(중복 허용 → 비활성 없음).
 * {@link Decoration} 엔티티 대신 이 record로 내보내 직렬화 표면을 좁힌다(id·category 등 미노출, 식물 팔레트와 같은 record 관례).
 *
 * @param code     소품 code
 * @param name     표시 이름(한글)
 * @param emoji    비주얼 이모지(SVG 미적용 종의 폴백)
 * @param spriteId 코드 벡터 SVG 스프라이트 식별자. null이면 이모지로 폴백
 */
public record DecorationOption(String code, String name, String emoji, String spriteId) {
}
