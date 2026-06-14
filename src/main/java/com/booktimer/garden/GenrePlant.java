package com.booktimer.garden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 독서 정원의 <b>장르 매핑</b> 식물 카탈로그 한 종 (읽기 전용·정적 시드).
 *
 * <p>시간축 {@link Plant}(누적 목표 달성일로 해금)와 별개의 수집축이다 — 어떤 <b>장르</b>의 책을 완독하면
 * 그 장르 식물을 보유로 <b>유도</b>한다(부채 모델 N-058: 보유 이력을 저장하지 않음). 두 축은 해금 의미가
 * 근본적으로 다르므로(날짜 누적 vs 장르 완독) 카탈로그·테이블·계산을 분리했다(설계 §2.2 옵션 B — 시간축 무변경).
 *
 * <p>매칭 키는 {@link #genreLabel} — {@code ReadingProfileAggregator.primaryGenre}가 알라딘 카테고리에서
 * 뽑은 장르 대분류("소설/시/희곡" 등)와 <b>정확히 일치</b>해야 그 식물이 보유로 잡힌다. {@code genreLabel}이
 * {@code null}인 행은 <b>폴백 식물</b>("이름 모를 들꽃")로, 시드 어느 장르에도 안 맞는 장르를 완독했을 때
 * "읽었는데 0개"인 허무를 막는다(설계 §2.5). 세터가 없어 시드(V36)로만 채워지는 불변 카탈로그다.
 */
@Entity
@Table(name = "genre_plant")
public class GenrePlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안정 식별 코드(시드·후속 참조용). 유니크. */
    @Column(nullable = false, unique = true)
    private String code;

    /**
     * 매칭 장르 라벨 — {@code primaryGenre()} 출력과 정확히 일치해야 보유로 잡힌다.
     * 폴백 식물은 {@code null}(특정 장르에 매이지 않고 "시드에 없는 모든 장르"를 담는다).
     */
    @Column(name = "genre_label")
    private String genreLabel;

    /** 표시 이름(한글). */
    @Column(nullable = false)
    private String name;

    /** 비주얼 이모지(utf8mb4). */
    @Column(nullable = false)
    private String emoji;

    /** 도감 진열 순서. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 코드 벡터 SVG 스프라이트 식별자(A2 후속). null이면 이모지로 폴백한다 — 정상 상태다(아직 SVG화 안 된 종).
     * 뷰는 {@code #sprite-{spriteId}} 심볼을 {@code <use>}로 참조한다. 시드(V41)에서만 채워지는 불변 메타.
     */
    @Column(name = "sprite_id", length = 50)
    private String spriteId;

    protected GenrePlant() {
        // JPA
    }

    private GenrePlant(String code, String genreLabel, String name, String emoji, int displayOrder, String spriteId) {
        this.code = code;
        this.genreLabel = genreLabel;
        this.name = name;
        this.emoji = emoji;
        this.displayOrder = displayOrder;
        this.spriteId = spriteId;
    }

    /**
     * 장르 식물 한 종을 만든다 — 운영은 시드(V36)로 채우므로 주 용도는 순수 도메인 테스트다
     * (패키지 안으로 노출 제한). {@code genreLabel}을 {@code null}로 주면 폴백 식물이 된다.
     * {@code spriteId}는 nullable(이모지 폴백) — SVG 미적용 종은 null로 전달한다(A2 후속).
     */
    static GenrePlant of(String code, String genreLabel, String name, String emoji, int displayOrder, String spriteId) {
        return new GenrePlant(code, genreLabel, name, emoji, displayOrder, spriteId);
    }

    /** 특정 장르에 매이지 않고 "시드에 없는 장르"를 담는 폴백 식물인가. */
    public boolean isFallback() {
        return genreLabel == null;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getGenreLabel() {
        return genreLabel;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    /** 코드 벡터 SVG 스프라이트 식별자 — null이면 이모지 폴백(A2 후속). */
    public String getSpriteId() {
        return spriteId;
    }
}
