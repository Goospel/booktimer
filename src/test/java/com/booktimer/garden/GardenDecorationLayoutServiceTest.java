package com.booktimer.garden;

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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정원 꾸미기 <b>소품</b>(데코) 배치 서비스 검증 (실제 빈·H2·자체 시드 카탈로그 — 살아있는 정원 게임 Phase 3).
 *
 * <p>소품은 식물과 근본적으로 다르다(설계 §1) — 그 차이를 경계로 본다:
 * <ul>
 *   <li><b>중복 허용</b> — 같은 소품을 여러 개(식물 "한 식물 한 번"의 반전, 핵심 distinct 실패).</li>
 *   <li><b>보유 무관</b> — 카탈로그 존재만 검증(보유 위조 방어 없음).</li>
 *   <li><b>z 통합 병합</b> — 식물+소품을 한 z 스케일로 교차 정렬({@code layoutItemsOf}).</li>
 *   <li><b>개수 cap</b>·<b>고아 제외</b>(카탈로그에서 빠진 코드)·<b>IDOR</b>·<b>좌표/변형 범위</b>.</li>
 * </ul>
 * Flyway는 테스트에서 꺼져 있어 카탈로그(소품·식물)를 {@code @BeforeEach}로 직접 심는다.
 */
@SpringBootTest
@Transactional
class GardenDecorationLayoutServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired private UserRegistrationService registrationService;
    @Autowired private ReadingSessionRepository sessionRepository;
    @Autowired private PlantRepository plantRepository;
    @Autowired private DecorationRepository decorationRepository;
    @Autowired private GardenDecorationPlacementRepository decorationPlacementRepository;
    @Autowired private GardenLayoutService layoutService;
    @Autowired private Clock clock;

    @BeforeEach
    void seedCatalog() {
        // 소품 카탈로그 — 보유 무관(해금 임계 없음).
        decorationRepository.save(Decoration.of("bench", "벤치", "🪑", "bench", "FURNITURE", 1));
        decorationRepository.save(Decoration.of("pond", "연못", "💧", "pond", "WATER", 2));
        decorationRepository.save(Decoration.of("rock", "바위", "🪨", null, "NATURE", 3)); // spriteId 미지정(이모지 폴백)
        // 식물 카탈로그 — z 병합 테스트에서 식물 1종 보유시키려고 둔다(시간축 sprout 임계 1).
        plantRepository.save(Plant.of("sprout", "새싹", "🌱", 1, 1, "sprout"));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "정원사", SEOUL, Role.USER, today());
    }

    /** 시간축 sprout(임계 1)을 보유시킨다 — 어제 하루 목표(3600초)를 채워 누적 달성 1. */
    private void grantTimeSprout(User user) {
        Instant start = today().minusDays(1).atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant();
        ReadingSession session = ReadingSession.start(user, start);
        session.end(start.plusSeconds(3600));
        sessionRepository.save(session);
    }

    private static DecorationPlacementRequest dreq(String code, double x, double y, int z) {
        return new DecorationPlacementRequest(code, x, y, z, 0, 1);
    }

    private static DecorationPlacementRequest dreq(String code, double x, double y, int z, double rotation, double scale) {
        return new DecorationPlacementRequest(code, x, y, z, rotation, scale);
    }

    @Test
    @DisplayName("소품을 좌표에 저장하면 layoutItemsOf가 소품 아이템(kind=decor)으로 돌려준다 (happy path)")
    void saveDecorations_thenLayoutReturnsDecor() {
        User user = register("decor-happy@booktimer.com");

        layoutService.saveDecorations(user, List.of(dreq("bench", 0.3, 0.4, 0)));

        List<PlacedItem> items = layoutService.layoutItemsOf(user);
        assertThat(items).hasSize(1);
        PlacedItem bench = items.get(0);
        assertThat(bench.kind()).isEqualTo(PlacedItem.KIND_DECOR);
        assertThat(bench.axis()).isNull(); // 소품은 축 없음
        assertThat(bench.code()).isEqualTo("bench");
        assertThat(bench.x()).isEqualTo(0.3);
        assertThat(bench.y()).isEqualTo(0.4);
        assertThat(bench.emoji()).isEqualTo("🪑");
        assertThat(bench.name()).isEqualTo("벤치");
        assertThat(bench.spriteId()).isEqualTo("bench");
    }

    @Test
    @DisplayName("같은 소품을 여러 개 놓을 수 있다 (중복 허용 — 식물 '한 식물 한 번'의 반전, 핵심)")
    void saveDecorations_allowsDuplicates() {
        User user = register("decor-dup@booktimer.com");

        // 식물이면 같은 (axis,code) 두 번은 거부되지만, 소품은 울타리·디딤돌처럼 여러 개가 정상이다.
        layoutService.saveDecorations(user, List.of(
                dreq("bench", 0.1, 0.1, 0),
                dreq("bench", 0.9, 0.9, 1)));

        List<PlacedItem> items = layoutService.layoutItemsOf(user);
        assertThat(items).hasSize(2);
        assertThat(items).extracting(PlacedItem::code).containsExactly("bench", "bench");
        assertThat(decorationPlacementRepository.findByUser(user)).hasSize(2); // 두 독립 행
    }

    @Test
    @DisplayName("카탈로그에 없는 소품 코드는 거부된다 (위조/직접 POST 방어)")
    void saveDecorations_rejectsUnknownCode() {
        User user = register("decor-unknown@booktimer.com");

        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("not_a_decor", 0.5, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(decorationPlacementRepository.findByUser(user)).isEmpty(); // 저장 자체가 안 됨
    }

    @Test
    @DisplayName("정규화 범위 밖(음수·1 초과) 좌표는 x·y 각각 거부된다 (경계 0·1 허용)")
    void saveDecorations_rejectsOutOfRangeCoords() {
        User user = register("decor-coord@booktimer.com");

        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", -0.01, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", 1.01, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", 0.5, -0.01, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", 0.5, 1.01, 0))))
                .isInstanceOf(IllegalArgumentException.class);

        layoutService.saveDecorations(user, List.of(dreq("bench", 0.0, 1.0, 0))); // 경계 허용
        assertThat(layoutService.layoutItemsOf(user)).hasSize(1);
    }

    @Test
    @DisplayName("rotation(0~360)·scale(0.5~2.0) 범위 밖은 거부되고, 범위 안은 왕복 보존된다")
    void saveDecorations_validatesAndPreservesTransform() {
        User user = register("decor-transform@booktimer.com");

        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", 0.5, 0.5, 0, -1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", 0.5, 0.5, 0, 361, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", 0.5, 0.5, 0, 0, 0.49))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.saveDecorations(user, List.of(dreq("bench", 0.5, 0.5, 0, 0, 2.01))))
                .isInstanceOf(IllegalArgumentException.class);

        layoutService.saveDecorations(user, List.of(dreq("bench", 0.5, 0.5, 0, 90, 1.5)));
        PlacedItem bench = layoutService.layoutItemsOf(user).get(0);
        assertThat(bench.rotation()).isEqualTo(90);
        assertThat(bench.scale()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("소품 개수 cap(MAX_DECORATIONS) 초과는 거부된다 (중복 허용이라 무한 방어)")
    void saveDecorations_rejectsOverCap() {
        User user = register("decor-cap@booktimer.com");

        List<DecorationPlacementRequest> overCap = new ArrayList<>();
        for (int i = 0; i <= GardenLayoutService.MAX_DECORATIONS; i++) { // cap+1개
            overCap.add(dreq("bench", 0.5, 0.5, i));
        }
        assertThatThrownBy(() -> layoutService.saveDecorations(user, overCap))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(decorationPlacementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("재저장은 본인 소품을 통째 교체하고, 다른 유저 소품은 건드리지 않는다 (교체 저장 + IDOR)")
    void saveDecorations_replacesAndIsScopedToOwner() {
        User me = register("decor-me@booktimer.com");
        User other = register("decor-other@booktimer.com");

        layoutService.saveDecorations(me, List.of(dreq("bench", 0.1, 0.1, 0)));
        layoutService.saveDecorations(other, List.of(dreq("pond", 0.7, 0.7, 0)));

        assertThat(layoutService.layoutItemsOf(me)).extracting(PlacedItem::code).containsExactly("bench");
        assertThat(layoutService.layoutItemsOf(other)).extracting(PlacedItem::code).containsExactly("pond");

        // me가 다른 소품으로 재저장 → 이전 bench는 빠지고 pond만, other는 불변.
        layoutService.saveDecorations(me, List.of(dreq("pond", 0.5, 0.5, 0)));
        assertThat(layoutService.layoutItemsOf(me)).extracting(PlacedItem::code).containsExactly("pond");
        assertThat(layoutService.layoutItemsOf(other)).extracting(PlacedItem::code).containsExactly("pond");

        // me 빈 저장 → me만 비고 other 유지.
        layoutService.saveDecorations(me, List.of());
        assertThat(layoutService.layoutItemsOf(me)).isEmpty();
        assertThat(layoutService.layoutItemsOf(other)).hasSize(1);
    }

    @Test
    @DisplayName("카탈로그에서 빠진 코드의 소품 배치는 layoutItemsOf에서 제외된다 (고아 방지 — 카탈로그 교집합)")
    void layoutItemsOf_excludesOrphanedDecoration() {
        User user = register("decor-orphan@booktimer.com");

        // 카탈로그에 없는 코드의 배치 행을 직접 심는다(예: 시드에서 빠진 옛 소품). layoutItemsOf가 카탈로그와
        // 교집합해 빼야 깨진 스프라이트가 화면에 새지 않는다(식물 유령 방지의 소품판, N-055 정신).
        decorationPlacementRepository.save(GardenDecorationPlacement.of(user, "ghost_decor", 0.5, 0.5, 0, 0, 1));
        layoutService.saveDecorations(user, List.of(dreq("bench", 0.2, 0.2, 0))); // 정상 소품 1개

        assertThat(layoutService.layoutItemsOf(user)).extracting(PlacedItem::code).containsExactly("bench");
    }

    @Test
    @DisplayName("layoutItemsOf는 식물+소품을 통합 z 오름차순으로 병합 정렬한다 (교차 정렬)")
    void layoutItemsOf_mergesPlantsAndDecorationsByZ() {
        User user = register("decor-merge@booktimer.com");
        grantTimeSprout(user); // 시간축 sprout 보유

        layoutService.saveLayout(user, new LayoutSaveRequest(
                List.of(new PlacementRequest(PlacementAxis.TIME, "sprout", 0.5, 0.5, 2, 0, 1)), // 식물 z=2
                List.of(dreq("bench", 0.1, 0.1, 0), dreq("pond", 0.9, 0.9, 5))));               // 소품 z=0, z=5

        List<PlacedItem> items = layoutService.layoutItemsOf(user);
        // z 오름차순 = bench(0) → sprout(2) → pond(5), 식물·소품이 교차한다.
        assertThat(items).extracting(PlacedItem::z).containsExactly(0, 2, 5);
        assertThat(items).extracting(PlacedItem::code).containsExactly("bench", "sprout", "pond");
        assertThat(items).extracting(PlacedItem::kind).containsExactly(
                PlacedItem.KIND_DECOR, PlacedItem.KIND_PLANT, PlacedItem.KIND_DECOR);
    }

    @Test
    @DisplayName("saveLayout은 식물과 소품을 함께 저장한다 (래퍼 오케스트레이션 — 식물 경로 위임)")
    void saveLayout_savesBothPlantsAndDecorations() {
        User user = register("decor-both@booktimer.com");
        grantTimeSprout(user);

        layoutService.saveLayout(user, new LayoutSaveRequest(
                List.of(new PlacementRequest(PlacementAxis.TIME, "sprout", 0.2, 0.3, 0, 0, 1)),
                List.of(dreq("bench", 0.7, 0.8, 1))));

        List<PlacedItem> items = layoutService.layoutItemsOf(user);
        assertThat(items).extracting(PlacedItem::kind)
                .containsExactlyInAnyOrder(PlacedItem.KIND_PLANT, PlacedItem.KIND_DECOR);
        // 식물 배치는 기존 layoutOf로도 보여야 한다(식물 경로 위임 확인).
        assertThat(layoutService.layoutOf(user)).extracting(PlacedPlant::code).containsExactly("sprout");
    }

    @Test
    @DisplayName("decorationCatalog는 카탈로그 전체를 진열 순서로 팔레트 옵션으로 낸다 (보유 무관)")
    void decorationCatalog_returnsAllInOrder() {
        List<DecorationOption> catalog = layoutService.decorationCatalog();
        assertThat(catalog).extracting(DecorationOption::code).containsExactly("bench", "pond", "rock");
        assertThat(catalog).extracting(DecorationOption::spriteId).containsExactly("bench", "pond", null);
    }
}
