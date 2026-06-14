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
 * <p>보유 식물 검증·격자 범위·중복·IDOR·축 분별·유령(보유 상실) 교집합 — 배치의 핵심 도메인 규칙을 전수로 본다.
 * 보유 집합은 {@link GardenService#view}를 재사용하므로(설계 §3) 보유를 만들려면 독서 실적 픽스처(세션·완독책)를 심는다.
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
    @Autowired private GardenService gardenService;
    @Autowired private GardenLayoutService layoutService;
    @Autowired private Clock clock;

    // 운영 시드(V35·V36·V38)와 디커플 — Flyway는 테스트에서 꺼져 있어 직접 심는다.
    // axis 분별 검증을 위해 시간축과 장르축에 '겹치는 code'(sprout)를 일부러 둔다(code는 테이블 안에서만 유니크).
    @BeforeEach
    void seedCatalog() {
        plantRepository.save(Plant.of("sprout", "새싹", "🌱", 1, 1, "sprout")); // A2: SVG 스프라이트 보유
        plantRepository.save(Plant.of("herb", "허브", "🌿", 2, 3, null));       // spriteId 미지정(이모지 폴백)

        genrePlantRepository.save(GenrePlant.of("sprout", "소설/시/희곡", "소설나무", "🌳", 1)); // TIME과 같은 code, 다른 축
        genrePlantRepository.save(GenrePlant.of("econ", "경제경영", "경제선인장", "🌵", 2));

        diversityPlantRepository.save(DiversityPlant.of("author_1", DiversityKind.AUTHOR, 1, "작가 새싹", "🖋️", 1));
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

    private static PlacementRequest req(PlacementAxis axis, String code, int cell) {
        return new PlacementRequest(axis, code, cell);
    }

    @Test
    @DisplayName("보유 식물을 격자에 저장하면 layoutOf가 셀·메타와 함께 돌려준다 (happy path)")
    void save_thenLayoutReturnsPlaced() {
        User user = register("place-happy@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 0),
                req(PlacementAxis.GENRE, "econ", 5)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).hasSize(2);
        assertThat(layout).extracting(PlacedPlant::cell).containsExactly(0, 5); // 셀 오름차순 정렬
        PlacedPlant sprout = layout.stream().filter(p -> p.cell() == 0).findFirst().orElseThrow();
        assertThat(sprout.axis()).isEqualTo(PlacementAxis.TIME);
        assertThat(sprout.code()).isEqualTo("sprout");
        assertThat(sprout.emoji()).isEqualTo("🌱");
        assertThat(sprout.name()).isEqualTo("새싹");
    }

    @Test
    @DisplayName("미보유 식물 배치 요청은 거부된다 (위조/직접 POST 방어)")
    void save_rejectsUnownedPlant() {
        User user = register("place-unowned@booktimer.com");
        grantTimeSproutAndGenreEcon(user); // herb(임계 3)는 미보유

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "herb", 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty(); // 저장 자체가 안 됨
    }

    @Test
    @DisplayName("같은 code라도 축이 다르면 다른 식물 — 보유한 TIME 'sprout'로 미보유 GENRE 'sprout'를 못 놓는다")
    void save_axisDiscriminates() {
        User user = register("place-axis@booktimer.com");
        metGoalOn(user, today().minusDays(1)); // TIME sprout만 보유, GENRE sprout(소설 완독 안 함)는 미보유

        // TIME sprout는 통과해야 한다.
        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0)));
        assertThat(layoutService.layoutOf(user)).extracting(PlacedPlant::axis).containsExactly(PlacementAxis.TIME);

        // GENRE sprout는 미보유라 거부.
        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.GENRE, "sprout", 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("격자 범위 밖(음수·총셀 이상) cell은 거부된다")
    void save_rejectsOutOfGridCell() {
        User user = register("place-grid@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        assertThatThrownBy(() -> layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", -1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", GardenLayoutService.GRID_CELLS))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("한 셀에 두 식물 / 한 식물을 두 셀 요청은 거부된다")
    void save_rejectsDuplicateCellAndPlant() {
        User user = register("place-dup@booktimer.com");
        grantTimeSproutAndGenreEcon(user);

        // 같은 셀에 둘
        assertThatThrownBy(() -> layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 3),
                req(PlacementAxis.GENRE, "econ", 3))))
                .isInstanceOf(IllegalArgumentException.class);

        // 같은 식물을 두 셀에
        assertThatThrownBy(() -> layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 1),
                req(PlacementAxis.TIME, "sprout", 2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장 후 그 식물을 보유 잃으면 layoutOf에서 빠진다 (유령 방지 — 교집합)")
    void layoutOf_excludesPlantsNoLongerOwned() {
        User user = register("place-ghost@booktimer.com");
        metGoalOn(user, today().minusDays(1));
        Book econBook = registerFinished(user, "경제책", "국내도서>경제경영>마케팅");

        layoutService.save(user, List.of(
                req(PlacementAxis.TIME, "sprout", 0),
                req(PlacementAxis.GENRE, "econ", 1)));
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

        layoutService.save(me, List.of(req(PlacementAxis.TIME, "sprout", 0)));
        layoutService.save(other, List.of(req(PlacementAxis.GENRE, "econ", 7)));

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
                req(PlacementAxis.TIME, "sprout", 0),
                req(PlacementAxis.GENRE, "econ", 5)));

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

        layoutService.save(user, List.of(req(PlacementAxis.TIME, "sprout", 0)));
        layoutService.save(user, List.of(req(PlacementAxis.GENRE, "econ", 10))); // sprout는 빠지고 econ만

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).extracting(PlacedPlant::code).containsExactly("econ");
        assertThat(layout).extracting(PlacedPlant::cell).containsExactly(10);
    }
}
