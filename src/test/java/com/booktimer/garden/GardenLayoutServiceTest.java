package com.booktimer.garden;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정원 꾸미기(배치) 서비스 검증 (실제 빈·H2·자체 시드 카탈로그).
 *
 * <p>보유 식물 검증·좌표 범위·겹침 허용·한 식물 한 번·IDOR·축 분별·유령(보유 상실) 교집합 — 배치의 핵심 도메인 규칙을
 * 전수로 본다(자유 위치 전환: 격자 셀 → 정규화 좌표(0~1)+zOrder, Phase 1). 보유 집합은 {@link GardenService#view}를
 * 재사용하므로(설계 §3) 보유를 만들려면 독서 실적 픽스처(세션·완독책)를 심는다.
 */
@SpringBootTest
@Transactional
class GardenLayoutServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired private UserRegistrationService registrationService;
    @Autowired private ReadingSessionRepository sessionRepository;
    @Autowired private PlantRepository plantRepository;
    @Autowired private GenrePlantRepository genrePlantRepository;
    @Autowired private DiversityPlantRepository diversityPlantRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private GardenPlacementRepository placementRepository;
    @Autowired private AuthorCharacterRepository authorCharacterRepository;
    @Autowired private GardenService gardenService;
    @Autowired private GardenLayoutService layoutService;
    @Autowired private Clock clock;

    // 운영 시드(V35·V36·V38)와 디커플 — Flyway는 테스트에서 꺼져 있어 직접 심는다.
    // axis 분별 검증을 위해 시간축과 장르축에 '겹치는 code'(sprout)를 일부러 둔다(code는 테이블 안에서만 유니크).
    @BeforeEach
    void seedCatalog() {
        plantRepository.save(Plant.of("sprout", "새싹", "🌱", 1, 1, "sprout")); // A2: SVG 스프라이트 보유
        plantRepository.save(Plant.of("herb", "허브", "🌿", 2, 3, null));       // spriteId 미지정(이모지 폴백)

        genrePlantRepository.save(GenrePlant.of("sprout", "소설/시/희곡", "소설나무", "🌳", 1, null)); // TIME과 같은 code, 다른 축
        genrePlantRepository.save(GenrePlant.of("econ", "경제경영", "경제선인장", "🌵", 2, null));

        diversityPlantRepository.save(DiversityPlant.of("author_1", DiversityKind.AUTHOR, 1, "작가 새싹", "🖋️", 1, null));

        // 작가 캐릭터 — AUTHOR 축 배치 검증용. prod 시드(V45)와 디커플.
        authorCharacterRepository.save(AuthorCharacter.of("han_gang", "한강", "소설가 한강", "🪶", 1, null));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "정원사", SEOUL, Role.USER, today());
    }

    /** 주어진 날짜에 하루 목표(3600초)를 채우는 완료 세션 — 시간축 보유 유도용. */
    private void metGoalOn(User user, LocalDate date) {
        Instant start = date.atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant();
        ReadingSession session = ReadingSession.start(user, start);
        session.end(start.plusSeconds(3600));
        sessionRepository.save(session);
    }

    private Book registerFinished(User user, String title, String category) {
        return bookRepository.save(Book.register(user, title, "저자", null, null, null, null,
                category, null, BookStatus.FINISHED));
    }

    /** 시간축 sprout(임계 1) + 장르축 econ(경제경영 완독)을 보유시킨다. */
    private void grantTimeSproutAndGenreEcon(User user) {
        metGoalOn(user, today().minusDays(1)); // 누적 달성 1 → 시간축 sprout 보유
        registerFinished(user, "경제책", "국내도서>경제경영>마케팅"); // 장르축 econ 보유
    }

    private static PlacementRequest req(PlacementAxis axis, String code, double x, double y, int z) {
        return new PlacementRequest(axis, code, x, y, z, 0, 1); // 변형 기본(rotation 0·scale 1)
    }

    private static PlacementRequest req(PlacementAxis axis, String code, double x, double y, int z,
                                        double rotation, double scale) {
        return new PlacementRequest(axis, code, x, y, z, rotation, scale);
    }

    @Test
    @DisplayName("보유 식물을 좌표에 저장하면 layoutOf가 좌표·메타와 함께 돌려준다 (happy path)")
    void save_thenLayoutReturnsPlaced() {
        User user = register("place-happy@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 0.2, 0.3, 0),
                req(PlacementAxis.GENRE, "econ", 0.7, 0.8, 1)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).hasSize(2);
        PlacedPlant sprout = layout.stream().filter(p -> p.code().equals("sprout")).findFirst().orElseThrow();
        assertThat(sprout.axis()).isEqualTo(PlacementAxis.TIME);
        assertThat(sprout.x()).isEqualTo(0.2);
        assertThat(sprout.y()).isEqualTo(0.3);
        assertThat(sprout.z()).isEqualTo(0);
        assertThat(sprout.emoji()).isEqualTo("🌱");
        assertThat(sprout.name()).isEqualTo("새싹");
    }

    @Test
    @DisplayName("layoutOf는 zOrder 오름차순(뒤→앞 렌더순)으로 돌려준다")
    void layoutOf_sortsByZOrder() {
        User user = register("place-zorder@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        // 저장 순서는 z가 큰 것 먼저 — 정렬되면 z 오름차순으로 뒤집혀야 한다.
        layoutService.save(user, List.of(
                req(PlacementAxis.GENRE, "econ", 0.5, 0.5, 5),
                req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 2)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).extracting(PlacedPlant::z).containsExactly(2, 5); // zOrder 오름차순
        assertThat(layout).extracting(PlacedPlant::code).containsExactly("sprout", "econ");
    }

    @Test
    @DisplayName("미보유 식물 배치 요청은 거부된다 (위조/직접 POST 방어)")
    void save_rejectsUnownedPlant() {
        User user = register("place-unowned@booktimer.com");
        grantTimeSproutAndGenreEcon(user); // herb(임계 3)는 미보유

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "herb", 0.1, 0.1, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty(); // 저장 자체가 안 됨
    }

    @Test
    @DisplayName("같은 code라도 축이 다르면 다른 식물 — 보유한 TIME 'sprout'로 미보유 GENRE 'sprout'를 못 놓는다")
    void save_axisDiscriminates() {
        User user = register("place-axis@booktimer.com");
        metGoalOn(user, today().minusDays(1)); // TIME sprout만 보유, GENRE sprout(소설 완독 안 함)는 미보유

        // TIME sprout는 통과해야 한다.
        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.1, 0.1, 0)));
        assertThat(layoutService.layoutOf(user)).extracting(PlacedPlant::axis).containsExactly(PlacementAxis.TIME);

        // GENRE sprout는 미보유라 거부.
        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.GENRE, "sprout", 0.1, 0.1, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정규화 범위 밖(음수·1 초과) 좌표는 x·y 각각 거부된다 (경계 0·1은 허용)")
    void save_rejectsOutOfRangeCoords() {
        User user = register("place-coord@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", -0.01, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 1.01, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, -0.01, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 1.01, 0))))
                .isInstanceOf(IllegalArgumentException.class);

        // 경계값(0·1)은 허용 — 저장돼야 한다.
        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.0, 1.0, 0)));
        assertThat(layoutService.layoutOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("두 식물을 같은 좌표에 겹쳐 놓을 수 있다 (자유 위치 — 겹침 허용)")
    void save_allowsOverlap() {
        User user = register("place-overlap@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        // 격자 시절엔 한 셀 하나만이라 거부됐지만, 자유 위치는 같은 좌표 겹침을 허용한다.
        layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0),
                req(PlacementAxis.GENRE, "econ", 0.5, 0.5, 1)));
        assertThat(layoutService.layoutOf(user)).hasSize(2);
    }

    @Test
    @DisplayName("같은 식물을 두 번 배치하는 요청은 거부된다 (한 식물 한 번 불변식 유지)")
    void save_rejectsDuplicatePlant() {
        User user = register("place-dup@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        assertThatThrownBy(() -> layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 0.1, 0.1, 0),
                req(PlacementAxis.TIME, "sprout", 0.9, 0.9, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장 후 그 식물을 보유 잃으면 layoutOf에서 빠진다 (유령 방지 — 교집합)")
    void layoutOf_excludesPlantsNoLongerOwned() {
        User user = register("place-ghost@booktimer.com");
        metGoalOn(user, today().minusDays(1));
        Book econBook = registerFinished(user, "경제책", "국내도서>경제경영>마케팅");

        layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 0.1, 0.1, 0),
                req(PlacementAxis.GENRE, "econ", 0.2, 0.2, 1)));
        assertThat(layoutService.layoutOf(user)).hasSize(2);

        // 경제책을 삭제 → 장르축 econ 보유 상실. 배치 행은 남아도 렌더에선 빠져야 한다.
        bookRepository.delete(econBook);
        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).extracting(PlacedPlant::code).containsExactly("sprout"); // econ 사라짐, sprout 유지
    }

    @Test
    @DisplayName("저장·조회는 본인 범위 — 한 유저 저장이 다른 유저 배치를 건드리지 않는다 (IDOR)")
    void save_isScopedToOwner() {
        User me = register("place-me@booktimer.com");
        User other = register("place-other@booktimer.com");
        grantTimeSproutAndGenreEcon(me);
        grantTimeSproutAndGenreEcon(other);

        layoutService.save(me, List.of(req(PlacementAxis.TIME, "sprout", 0.1, 0.1, 0)));
        layoutService.save(other, List.of(req(PlacementAxis.GENRE, "econ", 0.7, 0.7, 0)));

        // 각자 본인 것만 본다.
        assertThat(layoutService.layoutOf(me)).extracting(PlacedPlant::code).containsExactly("sprout");
        assertThat(layoutService.layoutOf(other)).extracting(PlacedPlant::code).containsExactly("econ");
        // me가 빈 배치로 재저장해도 other는 그대로(교체 저장이 본인 범위만 비운다).
        layoutService.save(me, List.of());
        assertThat(layoutService.layoutOf(me)).isEmpty();
        assertThat(layoutService.layoutOf(other)).hasSize(1);
    }

    @Test
    @DisplayName("배치된 시간축 식물에 spriteId가 PlacedPlant까지 결합된다 (A2 SVG 승격)")
    void layoutOf_carriesSpriteIdForTimeAxis() {
        User user = register("place-sprite@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 0.2, 0.3, 0),
                req(PlacementAxis.GENRE, "econ", 0.7, 0.8, 1)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        PlacedPlant sprout = layout.stream().filter(p -> p.code().equals("sprout")).findFirst().orElseThrow();
        assertThat(sprout.spriteId()).isEqualTo("sprout"); // 시간축 → 카탈로그 spriteId 결합
        PlacedPlant econ = layout.stream().filter(p -> p.code().equals("econ")).findFirst().orElseThrow();
        assertThat(econ.spriteId()).isNull(); // 타 축은 폴백(null)
    }

    @Test
    @DisplayName("재저장은 본인 배치를 통째 교체한다 (이전 배치가 남지 않음)")
    void save_replacesPreviousLayout() {
        User user = register("place-replace@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.1, 0.1, 0)));
        layoutService.save(user, List.of(req(PlacementAxis.GENRE, "econ", 0.6, 0.6, 0))); // sprout는 빠지고 econ만

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).extracting(PlacedPlant::code).containsExactly("econ");
        assertThat(layout).extracting(PlacedPlant::x).containsExactly(0.6);
    }

    @Test
    @DisplayName("rotation·scale을 저장하면 layoutOf가 그대로 돌려준다 (변형 왕복 보존)")
    void save_preservesRotationAndScale() {
        User user = register("place-transform@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.2, 0.3, 0, 90, 1.5)));

        PlacedPlant sprout = layoutService.layoutOf(user).get(0);
        assertThat(sprout.rotation()).isEqualTo(90);
        assertThat(sprout.scale()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("rotation/scale 없이 저장하면 기본값(0·1)으로 복원된다")
    void save_defaultsRotationScale() {
        User user = register("place-transform-default@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0)));

        PlacedPlant sprout = layoutService.layoutOf(user).get(0);
        assertThat(sprout.rotation()).isEqualTo(0);
        assertThat(sprout.scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("rotation 범위(0~360) 밖은 거부된다 (경계 0·360 허용)")
    void save_rejectsOutOfRangeRotation() {
        User user = register("place-rot@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0, -1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0, 361, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0, 360, 1)));
        assertThat(layoutService.layoutOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("scale 범위(0.5~2.0) 밖은 거부된다 (경계 0.5·2.0 허용)")
    void save_rejectsOutOfRangeScale() {
        User user = register("place-scale@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0, 0, 0.49))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0, 0, 2.01))))
                .isInstanceOf(IllegalArgumentException.class);
        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0.5, 0.5, 0, 0, 2.0)));
        assertThat(layoutService.layoutOf(user)).hasSize(1);
    }

    // --- AUTHOR axis: 작가 캐릭터 배치 검증 ------------------------------------------

    /** '한강 (지은이)' 완독 → han_gang 캐릭터(matchName="한강") 해금. */
    private Book grantAuthorHanGang(User user) {
        return bookRepository.save(Book.register(user, "소설책", "한강 (지은이)", null, null, null, null, null, null, BookStatus.FINISHED));
    }

    @Test
    @DisplayName("C2 풀어놓기: 보유한 AUTHOR 캐릭터도 save() 배치 요청은 거부된다 (배회로 전환, 좌표 저장 없음)")
    void save_author_isRejectedEvenWhenOwned() {
        User user = register("place-author-c2@booktimer.com");
        grantAuthorHanGang(user);  // 보유 상태이지만

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.AUTHOR, "han_gang", 0.3, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("미보유 AUTHOR 캐릭터 배치 요청은 거부된다 — ownedByKey에 AUTHOR 없음으로 자동 방어")
    void save_rejectsUnownedAuthorCharacter() {
        User user = register("place-author-unowned@booktimer.com");

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.AUTHOR, "han_gang", 0.5, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("C2 풀어놓기: DB에 잔존하는 AUTHOR 배치 행은 layoutOf 교집합에서 제외된다 (유령 방지 재사용)")
    void layoutOf_excludesResidualAuthorPlacementsFromDB() {
        User user = register("place-author-residual@booktimer.com");
        grantAuthorHanGang(user);  // 보유 상태이지만
        // save()를 우회해 DB에 직접 삽입 — C2 이전 V47 마이그레이션 전 잔존 데이터 시뮬레이션
        placementRepository.save(GardenPlacement.of(user, PlacementAxis.AUTHOR, "han_gang", 0.3, 0.5, 0, 0, 1));

        // layoutOf()의 ownedByKey 교집합: AUTHOR는 ownedPlants()에서 제거되어 렌더에서 제외
        assertThat(layoutService.layoutOf(user)).isEmpty();
    }
}
