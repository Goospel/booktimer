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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트랙 B 숨은 레시피 발견의 서비스 배선 검증 (실제 빈·H2·자체 시드 카탈로그).
 *
 * <p>레시피 만족 판정 자체는 {@link RecipeEvaluatorTest}가 순수로 전수한다. 여기선 조회({@code view}) 시
 * 평가 → 발견 저장 → 영속/멱등/격리가 실제 DB를 거쳐 배선되는지만 본다. 핵심은 트랙 A와 다른 철학:
 * 트랙 B는 발견을 <b>영구 저장</b>해 책장이 바뀌어도 보유가 유지된다(설계 §2.2).
 *
 * <p>운영 평가는 {@link RecipeDefinition#ALL}을 쓰므로 테스트도 실제 ALL 레시피를 만족시키는 책장을 만든다.
 * 레시피 "lotus" = 완독〈소설/시/희곡〉 + 읽고싶음〈과학〉.
 */
@SpringBootTest
@Transactional
class GardenServiceRecipeTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private GenrePlantRepository genrePlantRepository;
    @Autowired
    private RecipePlantRepository recipePlantRepository;
    @Autowired
    private UserDiscoveredPlantRepository discoveredRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private GardenService gardenService;
    @Autowired
    private Clock clock;

    // 테스트 카탈로그 — Flyway는 테스트에서 꺼져 있어 운영 시드(V35·V36·V37)가 안 돈다. 직접 심는다.
    // view()는 세 카탈로그를 모두 읽으므로 시간축·장르축도 최소 시드한다(트랙 B만 보지만 배선상 필요).
    @BeforeEach
    void seedCatalog() {
        plantRepository.save(Plant.of("sprout", "새싹", "🌱", 1, 1, null));
        genrePlantRepository.save(GenrePlant.of("novel", "소설/시/희곡", "소설나무", "🌳", 1));

        // 트랙 B 결과 식물 카탈로그 — 실제 ALL 코드와 일치시켜 발견이 렌더로 이어지는지 본다.
        recipePlantRepository.save(RecipePlant.of("lotus", "연꽃", "💮", "감성으로 읽고 이성을 탐낸다", 1));
        recipePlantRepository.save(RecipePlant.of("explorer", "탐험가 야자열매", "🥥", "편식 없는 탐험가", 2));
        recipePlantRepository.save(RecipePlant.of("regular", "단골 당근", "🥕", "한 작가를 깊이 따라간다", 3));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "정원사", SEOUL, Role.USER, today());
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private Book registerBook(User user, String title, String category, BookStatus status) {
        return bookRepository.save(Book.register(user, title, "저자", null, null, null, null,
                category, null, status));
    }

    /** lotus 레시피를 만족시키는 책장: 완독 소설 1 + 읽고싶음 과학 1. */
    private void shelfForLotus(User user) {
        registerBook(user, "완독소설", "국내도서>소설/시/희곡>한국소설", BookStatus.FINISHED);
        registerBook(user, "읽고싶음과학", "국내도서>과학>물리", BookStatus.WANT_TO_READ);
    }

    private static DiscoveredPlantState recipe(GardenView view, String code) {
        return view.recipePlants().stream()
                .filter(s -> s.recipePlant().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("레시피를 처음 만족하면 발견을 저장하고 justDiscovered로 알린다")
    void firstDiscovery_savesAndReports() {
        User user = register("recipe-first@booktimer.com");
        shelfForLotus(user);

        GardenView view = gardenService.view(user);

        assertThat(recipe(view, "lotus").discovered()).isTrue();
        assertThat(view.justDiscovered()).contains("연꽃");
        assertThat(discoveredRepository.existsByUserAndPlantCode(user, "lotus")).isTrue();
    }

    @Test
    @DisplayName("이미 발견한 레시피는 재저장하지 않고 justDiscovered도 비운다 (멱등)")
    void rediscovery_isIdempotent() {
        User user = register("recipe-idem@booktimer.com");
        shelfForLotus(user);

        gardenService.view(user); // 1차 발견
        GardenView second = gardenService.view(user); // 2차 조회

        assertThat(recipe(second, "lotus").discovered()).isTrue();
        assertThat(second.justDiscovered()).isEmpty();
        assertThat(discoveredRepository.findByUser(user)).hasSize(1);
    }

    @Test
    @DisplayName("발견 후 책장이 바뀌어 레시피가 불만족이 돼도 보유는 유지된다 (영속 — 트랙 A와 다른 철학 §2.2)")
    void discovery_persistsThoughShelfChanges() {
        User user = register("recipe-persist@booktimer.com");
        registerBook(user, "완독소설", "국내도서>소설/시/희곡>한국소설", BookStatus.FINISHED);
        Book wantScience = registerBook(user, "읽고싶음과학", "국내도서>과학>물리", BookStatus.WANT_TO_READ);

        assertThat(recipe(gardenService.view(user), "lotus").discovered()).isTrue(); // 발견

        // 읽고싶음 과학 책을 빼면 lotus 조건이 깨진다 — 유도였다면 사라질 상황.
        bookRepository.delete(wantScience);

        GardenView after = gardenService.view(user);
        assertThat(recipe(after, "lotus").discovered()).isTrue(); // 그래도 보유 유지
        assertThat(discoveredRepository.existsByUserAndPlantCode(user, "lotus")).isTrue();
    }

    @Test
    @DisplayName("발견은 사용자별로 격리된다 — 남의 발견이 새지 않는다")
    void discovery_isolatedPerUser() {
        User discoverer = register("recipe-mine@booktimer.com");
        shelfForLotus(discoverer);
        gardenService.view(discoverer);

        User other = register("recipe-other@booktimer.com");
        GardenView otherView = gardenService.view(other);

        assertThat(recipe(otherView, "lotus").discovered()).isFalse();
        assertThat(otherView.recipePlants()).noneMatch(DiscoveredPlantState::discovered);
        assertThat(discoveredRepository.existsByUserAndPlantCode(other, "lotus")).isFalse();
    }

    @Test
    @DisplayName("미발견 레시피는 '???' 미스터리 슬롯으로 집계된다 (조건 숨김 §2.5)")
    void undiscovered_countedAsMysterySlots() {
        User user = register("recipe-mystery@booktimer.com");
        shelfForLotus(user); // lotus만 발견 가능(완독 1종·작가 1권이라 explorer·regular 불충족)

        GardenView view = gardenService.view(user);

        // 카탈로그 3종 중 1종 발견 → 미스터리 슬롯 2.
        assertThat(view.recipePlants()).hasSize(3);
        assertThat(view.recipePlants()).filteredOn(DiscoveredPlantState::discovered).hasSize(1);
        assertThat(view.mysterySlotCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("책이 없으면 발견 0 · 모든 레시피 미발견 (null-state 누수 가드)")
    void nullState_noDiscovery() {
        User user = register("recipe-empty@booktimer.com");

        GardenView view = gardenService.view(user);

        assertThat(view.recipePlants()).isNotEmpty();
        assertThat(view.recipePlants()).noneMatch(DiscoveredPlantState::discovered);
        assertThat(view.mysterySlotCount()).isEqualTo(3);
        assertThat(view.justDiscovered()).isEmpty();
    }
}
