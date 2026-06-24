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
 * 독서 마을 서비스 배선 검증 (실제 빈·H2·자체 시드 카탈로그).
 *
 * <p>식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration)은 제거됨.
 * AUTHOR 캐릭터·BUILDING 건물 2축의 배선 확인.
 */
@SpringBootTest
@Transactional
class GardenServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private AuthorCharacterRepository authorCharacterRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private GardenService gardenService;
    @Autowired
    private Clock clock;

    @BeforeEach
    void seedCatalog() {
        // 작가 캐릭터 — 배선 확인용 한 종. prod 시드(V45)와 디커플.
        authorCharacterRepository.save(AuthorCharacter.of("han_gang", "한강", "소설가 한강", "🪶", 1, null));

        // 건물 — 배선 확인용 한 종. prod 시드(V46)와 디커플.
        buildingRepository.save(Building.of("minumsa", "민음사", "민음사", "🏛️", 3, 1, null));
    }

    /** 주어진 작가·출판사·상태로 책 한 권을 등록한다. */
    private void registerBookWith(User user, String title, String author, String publisher, BookStatus status) {
        bookRepository.save(Book.register(user, title, author, null, null, publisher, null,
                null, null, status));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "정원사", SEOUL, Role.USER, today());
    }

    // --- 건물축(BUILDING): 출판사 N권 완독 해금 배선 확인 ------------------------------------------

    @Test
    @DisplayName("민음사 3권 완독 → 건물 minumsa(임계3) 보유")
    void buildings_thresholdReached_owned() {
        User user = register("garden-building-owned@booktimer.com");
        registerBookWith(user, "책1", "저자가", "민음사", BookStatus.FINISHED);
        registerBookWith(user, "책2", "저자나", "민음사", BookStatus.FINISHED);
        registerBookWith(user, "책3", "저자다", "민음사", BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(building(view, "minumsa").owned()).isTrue();
        assertThat(view.ownedBuildingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("민음사 2권만 완독 → 건물 minumsa(임계3) 미보유 (임계 미달)")
    void buildings_belowThreshold_notOwned() {
        User user = register("garden-building-below@booktimer.com");
        registerBookWith(user, "책1", "저자가", "민음사", BookStatus.FINISHED);
        registerBookWith(user, "책2", "저자나", "민음사", BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(building(view, "minumsa").owned()).isFalse();
        assertThat(view.ownedBuildingCount()).isZero();
    }

    @Test
    @DisplayName("출판사 null 완독책만 있을 때 건물 누수 0 (N-055)")
    void buildings_nullPublisher_noLeak() {
        User user = register("garden-building-null@booktimer.com");
        registerBookWith(user, "수동등록", null, null, BookStatus.FINISHED);
        registerBookWith(user, "수동등록2", null, null, BookStatus.FINISHED);
        registerBookWith(user, "수동등록3", null, null, BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(view.buildings()).noneMatch(BuildingState::owned);
        assertThat(view.ownedBuildingCount()).isZero();
    }

    @Test
    @DisplayName("건물 보유 시 ownedPlants()에 axis=BUILDING으로 등장")
    void buildings_ownedAppearsInOwnedPlants() {
        User user = register("garden-building-palette@booktimer.com");
        registerBookWith(user, "책1", null, "민음사", BookStatus.FINISHED);
        registerBookWith(user, "책2", null, "민음사", BookStatus.FINISHED);
        registerBookWith(user, "책3", null, "민음사", BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        OwnedPlant buildingPlant = view.ownedPlants().stream()
                .filter(o -> o.axis() == PlacementAxis.BUILDING && o.code().equals("minumsa"))
                .findFirst().orElseThrow();
        assertThat(buildingPlant.emoji()).isEqualTo("🏛️");
    }

    // --- AUTHOR axis: 작가 캐릭터 배선 확인 ------------------------------------------

    @Test
    @DisplayName("큐레이션 작가 완독 → view().ownedCharacters()에 등장, ownedPlants()에는 없음 (C2 풀어놓기)")
    void authorCharacters_finishedAuthor_owned() {
        User user = register("garden-author-owned@booktimer.com");
        registerBookWith(user, "소설", "한강 (지은이)", null, BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(authorCharacter(view, "han_gang").owned()).isTrue();
        assertThat(view.ownedAuthorCharacterCount()).isEqualTo(1);
        // C2: AUTHOR는 배회 전용 — ownedCharacters()로 노출, ownedPlants()엔 없음
        OwnedCharacter ch = view.ownedCharacters().stream()
                .filter(c -> c.code().equals("han_gang"))
                .findFirst().orElseThrow();
        assertThat(ch.emoji()).isEqualTo("🪶");
        assertThat(view.ownedPlants()).extracting(OwnedPlant::axis).doesNotContain(PlacementAxis.AUTHOR);
    }

    @Test
    @DisplayName("읽고싶음 책만 있으면 작가 캐릭터 미owned — 완독만 집계(파밍 방지)")
    void authorCharacters_wantToReadOnly_notOwned() {
        User user = register("garden-author-want@booktimer.com");
        registerBookWith(user, "소설", "한강 (지은이)", null, BookStatus.WANT_TO_READ);

        GardenView view = gardenService.view(user);

        assertThat(authorCharacter(view, "han_gang").owned()).isFalse();
        assertThat(view.ownedAuthorCharacterCount()).isZero();
    }

    @Test
    @DisplayName("null 작가 책만 완독 → 작가 캐릭터 누수 0 (N-055)")
    void authorCharacters_nullAuthor_noLeak() {
        User user = register("garden-author-null@booktimer.com");
        registerBookWith(user, "수동등록", null, null, BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(view.authorCharacters()).noneMatch(AuthorCharacterState::owned);
        assertThat(view.ownedAuthorCharacterCount()).isZero();
    }

    @Test
    @DisplayName("책이 없으면 건물·작가 캐릭터 전부 미보유 (null-state 누수 가드)")
    void nullState_noBooksAllLocked() {
        User user = register("garden-empty@booktimer.com");

        GardenView view = gardenService.view(user);

        assertThat(view.ownedBuildingCount()).isZero();
        assertThat(view.ownedAuthorCharacterCount()).isZero();
        assertThat(view.buildings()).noneMatch(BuildingState::owned);
        assertThat(view.authorCharacters()).noneMatch(AuthorCharacterState::owned);
    }

    private static BuildingState building(GardenView view, String code) {
        return view.buildings().stream()
                .filter(s -> s.building().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static AuthorCharacterState authorCharacter(GardenView view, String code) {
        return view.authorCharacters().stream()
                .filter(s -> s.character().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
