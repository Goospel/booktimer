package com.booktimer.garden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 서재의 <b>작가 캐릭터</b> 카탈로그 한 종 (읽기 전용·정적 시드, V45).
 *
 * <p>트랙 A의 다섯 번째 수집축이다 — <b>특정 작가의 책을 완독</b>하면 그 작가 캐릭터를 보유로
 * <b>유도</b>한다(부채 모델 N-058 — 보유 이력 저장 안 함). 다양성축({@link DiversityPlant})이
 * "distinct 작가 수 ≥ 임계"인 것과 달리 이 축은 <b>특정 작가를 읽었는가</b>를 묻는다.
 *
 * <p>매칭은 부분·정규화(contains+normalize): {@link #matchName}을 정규화해 완독책 작가
 * {@code Book.author}(알라딘 원문 그대로, 예: "한강 (지은이)")의 정규화 결과에 contains로 검색한다.
 * 역할 접미({@code (지은이)})·역자·공백 표기 차이를 흡수하기 위해서다.
 * 카탈로그 큐레이션으로 짧거나 모호한 {@code matchName}(부분문자열 충돌 위험)을 회피한다.
 *
 * <p>세터가 없어 시드(V45)로만 채워지는 불변 카탈로그다.
 */
@Entity
@Table(name = "author_character")
public class AuthorCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안정 식별 코드(시드·배치 참조용). 유니크. */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * 매칭 기준 작가명(예: "한강", "베르나르 베르베르").
     * {@link AuthorCharacterUnlockCalculator#normalize}로 정규화한 뒤 책 작가 정규화 결과에 contains로 검색한다.
     */
    @Column(name = "match_name", nullable = false, length = 200)
    private String matchName;

    /** 표시 이름(캐릭터다움 살린 한글, 예: "소설가 한강"). */
    @Column(nullable = false, length = 100)
    private String name;

    /** 비주얼 이모지(utf8mb4). */
    @Column(nullable = false, length = 20)
    private String emoji;

    /**
     * 코드 벡터 SVG 스프라이트 식별자(저폴리 아트 후속). null이면 이모지로 폴백한다 — 정상 상태다.
     * 뷰는 {@code #sprite-{spriteId}} 심볼을 {@code <use>}로 참조한다.
     */
    @Column(name = "sprite_id", length = 50)
    private String spriteId;

    /** 도감 진열 순서. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected AuthorCharacter() {
        // JPA
    }

    private AuthorCharacter(String code, String matchName, String name, String emoji,
                            int displayOrder, String spriteId) {
        this.code = code;
        this.matchName = matchName;
        this.name = name;
        this.emoji = emoji;
        this.displayOrder = displayOrder;
        this.spriteId = spriteId;
    }

    /**
     * 작가 캐릭터 한 종을 만든다 — 운영은 시드(V45)로 채우므로 주 용도는 순수 도메인 테스트다
     * (패키지 안으로 노출 제한). {@code spriteId}는 nullable(이모지 폴백) — 저폴리 아트 미적용 종은 null.
     */
    static AuthorCharacter of(String code, String matchName, String name, String emoji,
                              int displayOrder, String spriteId) {
        return new AuthorCharacter(code, matchName, name, emoji, displayOrder, spriteId);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getMatchName() { return matchName; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public String getSpriteId() { return spriteId; }
    public int getDisplayOrder() { return displayOrder; }
}
