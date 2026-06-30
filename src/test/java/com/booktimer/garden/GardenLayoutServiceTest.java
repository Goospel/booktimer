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
 * 마을 배치 엔진 — 건물 은퇴 후 inert(배치 콘텐츠 0) 안전 가드.
 *
 * <p>건물(BUILDING)축이 은퇴돼 {@code ownedPlants()}가 항상 비므로, 배치 엔진은 어떤 배치도 받지 않고
 * (저장 거부), DB에 잔존하는 배치 행(BUILDING·AUTHOR·구식 축)은 렌더에서 모두 탈락한다(고아 방어).
 * happy-path(좌표·z·변형·중복·IDOR 등)는 배치 가능한 보유 콘텐츠가 사라져 함께 은퇴 — 엔진 자체 제거는 후속(PR-2).
 */
@SpringBootTest
@Transactional
class GardenLayoutServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired private UserRegistrationService registrationService;
    @Autowired private BookRepository bookRepository;
    @Autowired private GardenPlacementRepository placementRepository;
    @Autowired private AuthorCharacterRepository authorCharacterRepository;
    @Autowired private GardenLayoutService layoutService;
    @Autowired private Clock clock;

    @BeforeEach
    void seedCatalog() {
        authorCharacterRepository.save(AuthorCharacter.of("han_gang", "한강", "소설가 한강", "🪶", 1, null));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "마을사람", SEOUL, Role.USER, today());
    }

    private static PlacementRequest req(PlacementAxis axis, String code, double x, double y, int z) {
        return new PlacementRequest(axis, code, x, y, z, 0, 1);
    }

    // ── 건물 은퇴 후 엔진 inert 안전 가드 ───────────────────────────────────────────

    @Test
    @DisplayName("건물 은퇴: 출판사 책을 완독해도 ownedPlants()가 비어 BUILDING 배치는 거부된다 (배치 대상 0)")
    void save_buildingRejected_nothingOwned() {
        User user = register("layout-bld-reject@booktimer.com");
        // 민음사 책 완독 — 그러나 건물 카탈로그가 은퇴돼 보유로 잡히지 않는다.
        bookRepository.save(Book.register(user, "민음사책", null, null, null, "민음사", null, null, null, BookStatus.FINISHED));

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.BUILDING, "minumsa", 0.5, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("보유 AUTHOR(배회 전용)·구식 축(TIME) 배치 요청도 모두 거부된다")
    void save_authorAndDeprecatedAxesRejected() {
        User user = register("layout-axis-reject@booktimer.com");
        bookRepository.save(Book.register(user, "소설책", "한강 (지은이)", null, null, null, null, null, null, BookStatus.FINISHED));

        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.AUTHOR, "han_gang", 0.3, 0.5, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layoutService.save(user,
                List.of(req(PlacementAxis.TIME, "sprout", 0.1, 0.1, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(placementRepository.findByUser(user)).isEmpty();
    }

    @Test
    @DisplayName("빈 배치 저장은 허용된다 (현행 유지)")
    void save_emptyAllowed() {
        User user = register("layout-empty@booktimer.com");
        layoutService.save(user, List.of());
        assertThat(layoutService.layoutOf(user)).isEmpty();
    }

    @Test
    @DisplayName("고아 방어(회귀 핵심): DB에 잔존하는 배치 행(BUILDING·AUTHOR·TIME)은 layoutOf에서 모두 탈락한다")
    void layoutOf_excludesAllResidualPlacements() {
        User user = register("layout-orphan@booktimer.com");
        // save()를 우회해 DB에 직접 삽입 — 건물 은퇴·마을 전환 이전 잔존 데이터 시뮬레이션.
        placementRepository.save(GardenPlacement.of(user, PlacementAxis.BUILDING, "minumsa", 0.1, 0.1, 0, 0, 1));
        placementRepository.save(GardenPlacement.of(user, PlacementAxis.AUTHOR, "han_gang", 0.3, 0.5, 0, 0, 1));
        placementRepository.save(GardenPlacement.of(user, PlacementAxis.TIME, "sprout", 0.2, 0.2, 0, 0, 1));

        // ownedPlants() 비었으므로 교집합·BUILDING 필터로 전부 제외 — 렌더 0, 크래시 0.
        assertThat(layoutService.layoutOf(user)).isEmpty();
    }
}
