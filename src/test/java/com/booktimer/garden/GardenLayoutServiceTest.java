package com.booktimer.garden;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마을 꾸미기(BUILDING 배치) 서비스 검증 (실제 빈·H2·자체 시드 카탈로그).
 *
 * <p>식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration)은 제거됨.
 * BUILDING 축 배치·보유 검증·좌표 범위·중복·IDOR·고아 행 필터를 전수 검증한다.
 */
@SpringBootTest
@Transactional
class GardenLayoutServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired private UserRegistrationService registrationService;
    @Autowired private BookRepository bookRepository;
    @Autowired private BuildingRepository buildingRepository;
    @Autowired private GardenPlacementRepository placementRepository;
    @Autowired private AuthorCharacterRepository authorCharacterRepository;
    @Autowired private GardenService gardenService;
    @Autowired private GardenLayoutService layoutService;
    @Autowired private Clock clock;

    /**
     * 운영 시드(V46)와 디커플 — Flyway는 테스트에서 꺼져 있어 직접 심는다.
     * Building: 임계1(minumsa)·임계3(changbi) — 2종으로 복수·단수 보유 시나리오 커버.
     */
    @BeforeEach
    void seedCatalog() {
        buildingRepository.save(Building.of("minumsa", "민음사", "민음사", "🏛️", 1, 1, "minumsa")); // 임계 1, SVG 스프라이트
        buildingRepository.save(Building.of("changbi", "창비", "창비", "🏟️", 3, 2, null));           // 임계 3, 이모지 폴백
        authorCharacterRepository.save(AuthorCharacter.of("han_gang", "한강", "소설가 한강", "🪶", 1, null));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "마을사람", SEOUL, Role.USER, today());
    }

    /** 민음사(임계1) 보유: 1권 완독. */
    private void grantMinumsa(User user) {
        bookRepository.save(Book.register(user, "민음사책", null, null, null, "민음사", null, null, null, BookStatus.FINISHED));
    }

    /** 민음사(임계1)·창비(임계3) 동시 보유: 민음사 1권 + 창비 3권 완독. */
    private void grantMinumsaAndChangbi(User user) {
        grantMinumsa(user);
        for (int i = 0; i < 3; i++) {
            bookRepository.save(Book.register(user, "창비책" + i, null, null, null, "창비", null, null, null, BookStatus.FINISHED));
        }
    }

    private static PlacementRequest req(PlacementAxis axis, String code, double x, double y, int z) {
        return new PlacementRequest(axis, code, x, y, z, 0, 1);
    }

    private static PlacementRequest req(PlacementAxis axis, String code, double x, double y, int z,
                                        double rotation, double scale) {
        return new PlacementRequest(axis, code, x, y, z, rotation, scale);
    }

    // ── 고아 필터 (핵심·신규) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("고아 필터(회귀): DB에 직접 심은 axis=TIME 배치 행은 layoutOf()에서 제외된다")
    void layoutOf_excludesOrphanTimePlacement() {
        User user = register("orphan-time@booktimer.com");
        // TIME 행을 직접 삽입 — 마을 컨셉 전환 이전 잔존 데이터·DB 직접 조작 시뮬레이션
        placementRepository.save(GardenPlacement.of(user, PlacementAxis.TIME, "sprout", 0.1, 0.1, 0, 0, 1));

        // layoutOf(): ownedByKey(BUILDING만) 교집합 + 명시 BUILDING 필터 → TIME 행 제외
        assertThat(layoutService.layoutOf(user)).isEmpty();
    }

    @Test
    @DisplayName("고아 필터(회귀): save()는 BUILDING만 허용 — TIME/GENRE/DIVERSITY/RECIPE 거부")
    void save_deprecatedAxesRejected() {
        User user = register("deprecated-axis@booktimer.com");
        grantMinumsa(user); // BUILDING minumsa 보유

        // BUILDING은 통과
        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.1, 0.1, 0)));
        assertThat(layoutService.layoutOf(user)).extracting(PlacedPlant::axis)
                .containsExactly(PlacementAxis.BUILDING);

        // TIME axis — ownedByKey에 없어 거부 (소프트 제거 방어)
        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.TIME, "minumsa", 0.1, 0.1, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 배치 기본 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("보유 건물을 좌표에 저장하면 layoutOf가 좌표·메타와 함께 돌려준다 (happy path)")
    void save_thenLayoutReturnsPlaced() {
        User user = register("place-happy@booktimer.com");
        grantMinumsaAndChangbi(user);

        layoutService.save(user, List.of(
                req(PlacementAxis.BUILDING, "minumsa", 0.2, 0.3, 0),
                req(PlacementAxis.BUILDING, "changbi", 0.7, 0.8, 1)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).hasSize(2);
        PlacedPlant minumsa = layout.stream().filter(p -> p.code().equals("minumsa")).findFirst().orElseThrow();
        assertThat(minumsa.axis()).isEqualTo(PlacementAxis.BUILDING);
        assertThat(minumsa.x()).isEqualTo(0.2);
        assertThat(minumsa.y()).isEqualTo(0.3);
        assertThat(minumsa.z()).isEqualTo(0);
        assertThat(minumsa.emoji()).isEqualTo("🏛️");
        assertThat(minumsa.name()).isEqualTo("민음사");
    }

    @Test
    @DisplayName("layoutOf는 zOrder 오름차순(뒤→앞 렌더순)으로 돌려준다")
    void layoutOf_sortsByZOrder() {
        User user = register("place-zorder@booktimer.com");
        grantMinumsaAndChangbi(user);

        layoutService.save(user, List.of(
                req(PlacementAxis.BUILDING, "changbi", 0.5, 0.5, 5),
                req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 2)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).extracting(PlacedPlant::z).containsExactly(2, 5);
        assertThat(layout).extracting(PlacedPlant::code).containsExactly("minumsa", "changbi");
    }

    @Test
    @DisplayName("미보유 건물 배치 요청은 거부된다 (위조/직접 POST 방어)")
    void save_rejectsUnownedBuilding() {
        User user = register("place-unowned@booktimer.com");
        grantMinumsa(user); // minumsa만 보유, changbi(임계3)는 미보유

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "changbi", 0.1, 0.1, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("정규화 범위 밖(음수·1 초과) 좌표는 x·y 각각 거부된다 (경계 0·1은 허용)")
    void save_rejectsOutOfRangeCoords() {
        User user = register("place-coord@booktimer.com");
        grantMinumsa(user);

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", -0.01, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 1.01, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, -0.01, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 1.01, 0))))
                .isInstanceOf(IllegalArgumentException.class);

        // 경계값(0·1)은 허용
        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.0, 1.0, 0)));
        assertThat(layoutService.layoutOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("두 건물을 같은 좌표에 겹쳐 놓을 수 있다 (자유 위치 — 겹침 허용)")
    void save_allowsOverlap() {
        User user = register("place-overlap@booktimer.com");
        grantMinumsaAndChangbi(user);

        layoutService.save(user, List.of(
                req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0),
                req(PlacementAxis.BUILDING, "changbi", 0.5, 0.5, 1)));
        assertThat(layoutService.layoutOf(user)).hasSize(2);
    }

    @Test
    @DisplayName("같은 건물을 두 번 배치하는 요청은 거부된다 (한 건물 한 번 불변식 유지)")
    void save_rejectsDuplicateBuilding() {
        User user = register("place-dup@booktimer.com");
        grantMinumsa(user);

        assertThatThrownBy(() -> layoutService.save(user, List.of(
                req(PlacementAxis.BUILDING, "minumsa", 0.1, 0.1, 0),
                req(PlacementAxis.BUILDING, "minumsa", 0.9, 0.9, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장 후 그 건물을 보유 잃으면 layoutOf에서 빠진다 (유령 방지 — 교집합)")
    void layoutOf_excludesBuildingsNoLongerOwned() {
        User user = register("place-ghost@booktimer.com");
        Book minumsaBook = bookRepository.save(
                Book.register(user, "민음사책", null, null, null, "민음사", null, null, null, BookStatus.FINISHED));
        for (int i = 0; i < 3; i++) {
            bookRepository.save(Book.register(user, "창비책" + i, null, null, null, "창비", null, null, null, BookStatus.FINISHED));
        }

        layoutService.save(user, List.of(
                req(PlacementAxis.BUILDING, "minumsa", 0.1, 0.1, 0),
                req(PlacementAxis.BUILDING, "changbi", 0.2, 0.2, 1)));
        assertThat(layoutService.layoutOf(user)).hasSize(2);

        // 민음사책 삭제 → minumsa 보유 상실. 배치 행은 남아도 렌더에선 빠져야 한다.
        bookRepository.delete(minumsaBook);
        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).extracting(PlacedPlant::code).containsExactly("changbi");
    }

    @Test
    @DisplayName("저장·조회는 본인 범위 — 한 유저 저장이 다른 유저 배치를 건드리지 않는다 (IDOR)")
    void save_isScopedToOwner() {
        User me = register("place-me@booktimer.com");
        User other = register("place-other@booktimer.com");
        grantMinumsaAndChangbi(me);
        grantMinumsaAndChangbi(other);

        layoutService.save(me, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.1, 0.1, 0)));
        layoutService.save(other, List.of(req(PlacementAxis.BUILDING, "changbi", 0.7, 0.7, 0)));

        assertThat(layoutService.layoutOf(me)).extracting(PlacedPlant::code).containsExactly("minumsa");
        assertThat(layoutService.layoutOf(other)).extracting(PlacedPlant::code).containsExactly("changbi");
        layoutService.save(me, List.of());
        assertThat(layoutService.layoutOf(me)).isEmpty();
        assertThat(layoutService.layoutOf(other)).hasSize(1);
    }

    @Test
    @DisplayName("배치된 건물에 spriteId가 PlacedPlant까지 결합된다 (SVG 승격)")
    void layoutOf_carriesSpriteIdForBuilding() {
        User user = register("place-sprite@booktimer.com");
        grantMinumsaAndChangbi(user);

        layoutService.save(user, List.of(
                req(PlacementAxis.BUILDING, "minumsa", 0.2, 0.3, 0),
                req(PlacementAxis.BUILDING, "changbi", 0.7, 0.8, 1)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        PlacedPlant minumsa = layout.stream().filter(p -> p.code().equals("minumsa")).findFirst().orElseThrow();
        assertThat(minumsa.spriteId()).isEqualTo("minumsa"); // SVG 스프라이트 결합
        PlacedPlant changbi = layout.stream().filter(p -> p.code().equals("changbi")).findFirst().orElseThrow();
        assertThat(changbi.spriteId()).isNull(); // 미지정(이모지 폴백)
    }

    @Test
    @DisplayName("재저장은 본인 배치를 통째 교체한다 (이전 배치가 남지 않음)")
    void save_replacesPreviousLayout() {
        User user = register("place-replace@booktimer.com");
        grantMinumsaAndChangbi(user);

        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.1, 0.1, 0)));
        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "changbi", 0.6, 0.6, 0)));

        List<PlacedPlant> layout = layoutService.layoutOf(user);
        assertThat(layout).extracting(PlacedPlant::code).containsExactly("changbi");
        assertThat(layout).extracting(PlacedPlant::x).containsExactly(0.6);
    }

    @Test
    @DisplayName("rotation·scale을 저장하면 layoutOf가 그대로 돌려준다 (변형 왕복 보존)")
    void save_preservesRotationAndScale() {
        User user = register("place-transform@booktimer.com");
        grantMinumsa(user);

        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.2, 0.3, 0, 90, 1.5)));

        PlacedPlant minumsa = layoutService.layoutOf(user).get(0);
        assertThat(minumsa.rotation()).isEqualTo(90);
        assertThat(minumsa.scale()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("rotation/scale 없이 저장하면 기본값(0·1)으로 복원된다")
    void save_defaultsRotationScale() {
        User user = register("place-transform-default@booktimer.com");
        grantMinumsa(user);

        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0)));

        PlacedPlant minumsa = layoutService.layoutOf(user).get(0);
        assertThat(minumsa.rotation()).isEqualTo(0);
        assertThat(minumsa.scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("rotation 범위(0~360) 밖은 거부된다 (경계 0·360 허용)")
    void save_rejectsOutOfRangeRotation() {
        User user = register("place-rot@booktimer.com");
        grantMinumsa(user);

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0, -1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0, 361, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0, 360, 1)));
        assertThat(layoutService.layoutOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("scale 범위(0.5~2.0) 밖은 거부된다 (경계 0.5·2.0 허용)")
    void save_rejectsOutOfRangeScale() {
        User user = register("place-scale@booktimer.com");
        grantMinumsa(user);

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0, 0, 0.49))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0, 0, 2.01))))
                .isInstanceOf(IllegalArgumentException.class);
        layoutService.save(user, List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0, 0, 2.0)));
        assertThat(layoutService.layoutOf(user)).hasSize(1);
    }

    // ── AUTHOR axis: 작가 캐릭터 배치 거부 (C2 풀어놓기) ─────────────────────────────

    private Book grantAuthorHanGang(User user) {
        return bookRepository.save(Book.register(user, "소설책", "한강 (지은이)", null, null, null, null, null, null, BookStatus.FINISHED));
    }

    @Test
    @DisplayName("C2 풀어놓기: 보유한 AUTHOR 캐릭터도 save() 배치 요청은 거부된다 (배회로 전환, 좌표 저장 없음)")
    void save_author_isRejectedEvenWhenOwned() {
        User user = register("place-author-c2@booktimer.com");
        grantAuthorHanGang(user);

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.AUTHOR, "han_gang", 0.3, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("미보유 AUTHOR 캐릭터 배치 요청은 거부된다")
    void save_rejectsUnownedAuthorCharacter() {
        User user = register("place-author-unowned@booktimer.com");

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.AUTHOR, "han_gang", 0.5, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("C2 풀어놓기: DB에 잔존하는 AUTHOR 배치 행은 layoutOf 교집합·BUILDING 필터에서 제외된다")
    void layoutOf_excludesResidualAuthorPlacementsFromDB() {
        User user = register("place-author-residual@booktimer.com");
        grantAuthorHanGang(user);
        // save()를 우회해 DB에 직접 삽입 — C2 이전·마을 전환 이전 잔존 데이터 시뮬레이션
        placementRepository.save(GardenPlacement.of(user, PlacementAxis.AUTHOR, "han_gang", 0.3, 0.5, 0, 0, 1));

        // layoutOf()의 명시 BUILDING 필터 + ownedByKey 교집합: AUTHOR 행 제외
        assertThat(layoutService.layoutOf(user)).isEmpty();
    }
}
