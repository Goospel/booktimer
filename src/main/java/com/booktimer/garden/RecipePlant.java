package com.booktimer.garden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 독서 정원 트랙 B의 <b>숨은 레시피 결과 식물</b> 카탈로그 한 종 (읽기 전용·정적 시드).
 *
 * <p>트랙 A 식물(시간축 {@link Plant}·장르축 {@link GenrePlant})과 별개의 수집축이다 — 완독+읽고싶음 책의
 * <b>조합</b>을 발견하면 얻는다. 조건↔식물 연결은 코드({@link RecipeDefinition})에 두고(임의 술어라 SQL화가
 * 부자연), 이 테이블엔 결과 식물의 표시 메타(이름·이모지·스토리)만 시드한다(설계 §2.3). {@link #code}가
 * {@code RecipeDefinition.plantCode}와 일치해야 발견이 이 카탈로그 칸으로 렌더된다.
 *
 * <p>트랙 A와 달리 발견은 <b>저장</b>된다({@link UserDiscoveredPlant}) — 발견은 "처음 만족된 순간"의 이벤트라
 * 박아야 하고, 책장이 바뀌어도 보유가 유지돼야 하기 때문(설계 §2.2). 세터가 없어 시드(V37)로만 채워지는
 * 불변 카탈로그다. 베타 큐레이션이라 식물 목록은 커스텀 카탈로그 단계에서 교체된다(계획 §8).
 */
@Entity
@Table(name = "recipe_plant")
public class RecipePlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안정 식별 코드 — {@link RecipeDefinition#plantCode}와 일치해야 발견이 이 식물로 잡힌다. 유니크. */
    @Column(nullable = false, unique = true)
    private String code;

    /** 표시 이름(한글). */
    @Column(nullable = false)
    private String name;

    /** 비주얼 이모지(utf8mb4). */
    @Column(nullable = false)
    private String emoji;

    /** 발견 시 보여줄 짧은 스토리(조합의 의미). */
    @Column(nullable = false)
    private String story;

    /** 도감 진열 순서. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 코드 벡터 SVG 스프라이트 식별자(A2 후속). null이면 이모지로 폴백한다 — 정상 상태다(아직 SVG화 안 된 종).
     * 뷰는 {@code #sprite-{spriteId}} 심볼을 {@code <use>}로 참조한다. 시드(V41)에서만 채워지는 불변 메타.
     */
    @Column(name = "sprite_id", length = 50)
    private String spriteId;

    protected RecipePlant() {
        // JPA
    }

    private RecipePlant(String code, String name, String emoji, String story, int displayOrder, String spriteId) {
        this.code = code;
        this.name = name;
        this.emoji = emoji;
        this.story = story;
        this.displayOrder = displayOrder;
        this.spriteId = spriteId;
    }

    /**
     * 레시피 결과 식물 한 종을 만든다 — 운영은 시드(V37)로 채우므로 주 용도는 순수 도메인 테스트다
     * (패키지 안으로 노출 제한). {@code spriteId}는 nullable(이모지 폴백) — SVG 미적용 종은 null로 전달한다(A2 후속).
     */
    static RecipePlant of(String code, String name, String emoji, String story, int displayOrder, String spriteId) {
        return new RecipePlant(code, name, emoji, story, displayOrder, spriteId);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getStory() {
        return story;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    /** 코드 벡터 SVG 스프라이트 식별자 — null이면 이모지 폴백(A2 후속). */
    public String getSpriteId() {
        return spriteId;
    }
}
