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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 독서 정원 서비스 배선 검증 (실제 빈·H2·자체 시드 카탈로그).
 *
 * <p>해금 판정 규칙 자체는 {@link PlantUnlockCalculatorTest}가 전수로 본다. 여기선 일자별 세션 집계 →
 * 목표 이력 → 카탈로그 조회 → 해금일·진척 조립이 실제 DB를 거쳐 제대로 배선되는지만 본다.
 * null-state(세션 0)에서 미보유 식물이 새지 않는지도 못 박는다(N-055).
 */
@SpringBootTest
@Transactional
class GardenServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private ReadingSessionRepository sessionRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private GenrePlantRepository genrePlantRepository;
    @Autowired
    private DiversityPlantRepository diversityPlantRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private GardenService gardenService;
    @Autowired
    private Clock clock;

    // 테스트 카탈로그 — 운영 시드(V35·V36)와 디커플해 배선만 본다.
    // Flyway는 테스트에서 꺼져 있어(application.properties) 시드가 안 도므로 직접 심는다.
    @BeforeEach
    void seedCatalog() {
        plantRepository.save(Plant.of("sprout", "새싹", "🌱", 1, 1, "sprout")); // A2: SVG 스프라이트 보유
        plantRepository.save(Plant.of("herb", "허브", "🌿", 2, 3, null));       // spriteId 미지정(이모지 폴백)
        plantRepository.save(Plant.of("clover", "클로버", "☘️", 3, 5, null));
        plantRepository.save(Plant.of("pot", "화분 모종", "🪴", 4, 7, null));

        genrePlantRepository.save(GenrePlant.of("novel", "소설/시/희곡", "소설나무", "🌳", 1, "novel")); // A2 후속: SVG 스프라이트 보유
        genrePlantRepository.save(GenrePlant.of("econ", "경제경영", "경제선인장", "🌵", 2, null));        // spriteId 미지정(이모지 폴백)
        genrePlantRepository.save(GenrePlant.of("wildflower", null, "이름 모를 들꽃", "🌼", 99, "wildflower"));

        // 다양성축 — 작가 식물(임계 1·3) + 출판사 식물(임계 1·3). prod 시드(V38)와 디커플.
        diversityPlantRepository.save(DiversityPlant.of("author_1", DiversityKind.AUTHOR, 1, "작가 새싹", "🖋️", 1, "author_1")); // A2 후속: SVG
        diversityPlantRepository.save(DiversityPlant.of("author_3", DiversityKind.AUTHOR, 3, "작가 나무", "✒️", 2, null));      // 미지정(폴백)
        diversityPlantRepository.save(DiversityPlant.of("publisher_1", DiversityKind.PUBLISHER, 1, "출판 새싹", "🏷️", 3, "publisher_1"));
        diversityPlantRepository.save(DiversityPlant.of("publisher_3", DiversityKind.PUBLISHER, 3, "출판 나무", "📦", 4, null));
    }

    /** 주어진 장르(category)·상태로 책 한 권을 등록한다. */
    private void registerBook(User user, String title, String category, BookStatus status) {
        bookRepository.save(Book.register(user, title, "저자", null, null, null, null,
                category, null, status));
    }

    /** 주어진 작가·출판사·상태로 책 한 권을 등록한다(다양성축 검증용). */
    private void registerBookWith(User user, String title, String author, String publisher, BookStatus status) {
        bookRepository.save(Book.register(user, title, author, null, null, publisher, null,
                null, null, status));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    /** 주어진 날짜에 하루 목표(기본 3600초)를 정확히 채우는 완료 세션을 만든다. */
    private void metGoalOn(User user, LocalDate date) {
        Instant start = date.atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant();
        ReadingSession session = ReadingSession.start(user, start);
        session.end(start.plusSeconds(3600));
        sessionRepository.save(session);
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "정원사", SEOUL, Role.USER, today());
    }

    @Test
    @DisplayName("목표를 N일 채우면 임계 이하 식물을 보유하고 진척·다음 해금이 정확하다")
    void ownedCountAndProgress() {
        User user = register("garden-owned@booktimer.com");
        // 최근 5일 각각 목표 달성 → 누적 달성일 5 → 임계 1,3,5 식물(새싹·허브·클로버) 보유.
        for (int back = 1; back <= 5; back++) {
            metGoalOn(user, today().minusDays(back));
        }

        GardenView view = gardenService.view(user);

        assertThat(view.achievedDays()).isEqualTo(5);
        assertThat(view.ownedCount()).isEqualTo(3);
        assertThat(view.totalCount()).isEqualTo(4);
        assertThat(view.plants()).filteredOn(PlantState::owned).extracting(s -> s.plant().getCode())
                .containsExactly("sprout", "herb", "clover");
        // 다음 해금 = 임계 7(화분 모종), 남은 2일.
        assertThat(view.daysToNextUnlock()).isEqualTo(2);
        assertThat(view.nextPlantName()).isEqualTo("화분 모종");
    }

    @Test
    @DisplayName("세션이 없으면 보유 0 · 모든 식물 미보유 (null-state 누수 가드 N-055)")
    void nullState_noSessions() {
        User user = register("garden-empty@booktimer.com");

        GardenView view = gardenService.view(user);

        assertThat(view.achievedDays()).isZero();
        assertThat(view.ownedCount()).isZero();
        assertThat(view.totalCount()).isEqualTo(4);
        assertThat(view.plants()).isNotEmpty();
        assertThat(view.plants()).noneMatch(PlantState::owned);
        // 빈 정원이어도 다음 해금은 첫 식물(새싹)을 가리킨다.
        assertThat(view.daysToNextUnlock()).isEqualTo(1);
        assertThat(view.nextPlantName()).isEqualTo("새싹");
    }

    @Test
    @DisplayName("해금일은 그 식물 임계번째 달성 날짜와 일치한다")
    void unlockedOn_isThresholdDate() {
        User user = register("garden-date@booktimer.com");
        // 달성일: today-5, today-4, today-3 (오름차순). 임계 1=새싹→1번째(today-5), 임계 3=허브→3번째(today-3).
        metGoalOn(user, today().minusDays(5));
        metGoalOn(user, today().minusDays(4));
        metGoalOn(user, today().minusDays(3));

        GardenView view = gardenService.view(user);

        assertThat(plant(view, "sprout").unlockedOn()).isEqualTo(today().minusDays(5));
        assertThat(plant(view, "herb").unlockedOn()).isEqualTo(today().minusDays(3));
        assertThat(plant(view, "clover").owned()).isFalse();
        assertThat(plant(view, "clover").unlockedOn()).isNull();
    }

    @Test
    @DisplayName("isNew는 해금일 7일 경계로 갈린다 — today-7은 NEW, today-8은 아님")
    void isNew_sevenDayBoundary() {
        User newer = register("garden-new@booktimer.com");
        metGoalOn(newer, today().minusDays(7)); // 7일 전 해금 → 경계 안(NEW)
        assertThat(plant(gardenService.view(newer), "sprout").isNew()).isTrue();

        User older = register("garden-old@booktimer.com");
        metGoalOn(older, today().minusDays(8)); // 8일 전 해금 → 경계 밖(NEW 아님)
        assertThat(plant(gardenService.view(older), "sprout").isNew()).isFalse();
    }

    @Test
    @DisplayName("완독한 장르의 식물만 보유 — 읽는중·읽고싶음 책은 장르 식물에 안 들어간다")
    void genrePlants_finishedOnly() {
        User user = register("garden-genre-finished@booktimer.com");
        registerBook(user, "완독소설", "국내도서>소설/시/희곡>한국소설", BookStatus.FINISHED);
        registerBook(user, "읽는중경제", "국내도서>경제경영>마케팅", BookStatus.READING);
        registerBook(user, "읽고싶음경제", "국내도서>경제경영>투자", BookStatus.WANT_TO_READ);

        GardenView view = gardenService.view(user);

        assertThat(view.totalGenreCount()).isEqualTo(3);
        assertThat(view.ownedGenreCount()).isEqualTo(1);
        assertThat(genrePlant(view, "novel").owned()).isTrue();
        // 경제는 완독이 아니라 미보유 — 파밍 방지(완독만 집계).
        assertThat(genrePlant(view, "econ").owned()).isFalse();
        assertThat(genrePlant(view, "wildflower").owned()).isFalse();
    }

    @Test
    @DisplayName("같은 장르 2권 완독 → 식물 1개로 흡수")
    void genrePlants_sameGenreCollapses() {
        User user = register("garden-genre-dup@booktimer.com");
        registerBook(user, "소설A", "국내도서>소설/시/희곡>한국소설", BookStatus.FINISHED);
        registerBook(user, "소설B", "국내도서>소설/시/희곡>외국소설", BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(view.ownedGenreCount()).isEqualTo(1);
        assertThat(genrePlant(view, "novel").owned()).isTrue();
    }

    @Test
    @DisplayName("category가 null인 완독책은 장르 식물 누수 0 (N-055)")
    void genrePlants_nullCategoryNoLeak() {
        User user = register("garden-genre-null@booktimer.com");
        registerBook(user, "수동등록완독", null, BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(view.ownedGenreCount()).isZero();
        assertThat(view.genrePlants()).noneMatch(GenrePlantState::owned);
    }

    @Test
    @DisplayName("책이 없으면 장르 식물 전부 미보유 (null-state 누수 가드)")
    void genrePlants_nullStateAllLocked() {
        User user = register("garden-genre-empty@booktimer.com");

        GardenView view = gardenService.view(user);

        assertThat(view.totalGenreCount()).isEqualTo(3);
        assertThat(view.ownedGenreCount()).isZero();
        assertThat(view.genrePlants()).isNotEmpty();
        assertThat(view.genrePlants()).noneMatch(GenrePlantState::owned);
    }

    @Test
    @DisplayName("시드에 없는 장르를 완독하면 폴백 식물(들꽃)이 보유로 잡힌다")
    void genrePlants_unknownGenreUnlocksFallback() {
        User user = register("garden-genre-fallback@booktimer.com");
        registerBook(user, "여행에세이", "국내도서>여행>국내여행", BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(genrePlant(view, "wildflower").owned()).isTrue();
        assertThat(genrePlant(view, "novel").owned()).isFalse();
    }

    @Test
    @DisplayName("완독한 책의 distinct 작가·출판사 수로 다양성 식물 보유 — 읽는중·읽고싶음은 제외")
    void diversityPlants_finishedOnly() {
        User user = register("garden-diversity-finished@booktimer.com");
        registerBookWith(user, "완독1", "김영하", "문학동네", BookStatus.FINISHED);
        registerBookWith(user, "완독2", "한강", "창비", BookStatus.FINISHED);
        // 읽는중·읽고싶음 작가/출판사를 섞어도 완독이 아니라 집계 제외(파밍 방지·책BTI 신호 일치).
        registerBookWith(user, "읽는중", "무라카미", "민음사", BookStatus.READING);
        registerBookWith(user, "읽고싶음", "베르나르", "열린책들", BookStatus.WANT_TO_READ);

        GardenView view = gardenService.view(user);

        // 완독 작가 2 → 임계 1 보유·임계 3 미보유(읽는중·읽고싶음까지 셌다면 4 ≥ 3이라 보유였을 것).
        assertThat(diversityPlant(view, "author_1").owned()).isTrue();
        assertThat(diversityPlant(view, "author_3").owned()).isFalse();
        assertThat(view.ownedAuthorCount()).isEqualTo(1);
        // 완독 출판사 2 → 임계 1 보유·임계 3 미보유.
        assertThat(diversityPlant(view, "publisher_1").owned()).isTrue();
        assertThat(diversityPlant(view, "publisher_3").owned()).isFalse();
        assertThat(view.ownedPublisherCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 작가·출판사로 2권 완독 → distinct 1로 흡수")
    void diversityPlants_sameAuthorCollapses() {
        User user = register("garden-diversity-dup@booktimer.com");
        registerBookWith(user, "책1", "김영하", "문학동네", BookStatus.FINISHED);
        registerBookWith(user, "책2", "김영하", "문학동네", BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(view.ownedAuthorCount()).isEqualTo(1);
        assertThat(view.ownedPublisherCount()).isEqualTo(1);
        assertThat(diversityPlant(view, "author_1").owned()).isTrue();
        assertThat(diversityPlant(view, "author_3").owned()).isFalse();
    }

    @Test
    @DisplayName("작가·출판사가 null인 완독책은 다양성 식물 누수 0 (N-055)")
    void diversityPlants_nullAuthorPublisherNoLeak() {
        User user = register("garden-diversity-null@booktimer.com");
        registerBookWith(user, "수동등록완독", null, null, BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(view.ownedAuthorCount()).isZero();
        assertThat(view.ownedPublisherCount()).isZero();
        assertThat(view.diversityPlants()).noneMatch(DiversityPlantState::owned);
    }

    @Test
    @DisplayName("책이 없으면 다양성 식물 전부 미보유 (null-state 누수 가드)")
    void diversityPlants_nullStateAllLocked() {
        User user = register("garden-diversity-empty@booktimer.com");

        GardenView view = gardenService.view(user);

        assertThat(view.totalAuthorCount()).isEqualTo(2);
        assertThat(view.totalPublisherCount()).isEqualTo(2);
        assertThat(view.diversityPlants()).isNotEmpty();
        assertThat(view.diversityPlants()).noneMatch(DiversityPlantState::owned);
    }

    @Test
    @DisplayName("작가축·출판사축 독립 — 작가 많고 출판사 적으면 출판사 식물이 작가 수에 휩쓸리지 않는다")
    void diversityPlants_axesIndependent() {
        User user = register("garden-diversity-indep@booktimer.com");
        // 작가는 셋 다 다르고(3), 출판사는 한 곳으로 몰아줌(1).
        registerBookWith(user, "책1", "작가가", "한출판사", BookStatus.FINISHED);
        registerBookWith(user, "책2", "작가나", "한출판사", BookStatus.FINISHED);
        registerBookWith(user, "책3", "작가다", "한출판사", BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        // 작가 3 → 임계 3 보유. 출판사 1 → 임계 3 미보유(작가 수에 안 휩쓸림)·임계 1만 보유.
        assertThat(diversityPlant(view, "author_3").owned()).isTrue();
        assertThat(diversityPlant(view, "publisher_3").owned()).isFalse();
        assertThat(diversityPlant(view, "publisher_1").owned()).isTrue();
        assertThat(view.ownedAuthorCount()).isEqualTo(2);    // author_1·author_3
        assertThat(view.ownedPublisherCount()).isEqualTo(1); // publisher_1만
    }

    private static DiversityPlantState diversityPlant(GardenView view, String code) {
        return view.diversityPlants().stream()
                .filter(s -> s.plant().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static GenrePlantState genrePlant(GardenView view, String code) {
        return view.genrePlants().stream()
                .filter(s -> s.genrePlant().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static PlantState plant(GardenView view, String code) {
        return view.plants().stream()
                .filter(s -> s.plant().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    // --- A2: SVG 식물 승격 — spriteId 전파 (시간축) ------------------------------------------

    @Test
    @DisplayName("시간축 식물의 spriteId가 ownedPlants 전파에 실린다 (A2 SVG 승격)")
    void spriteId_propagatesForTimeAxis() {
        User user = register("garden-sprite@booktimer.com");
        metGoalOn(user, today().minusDays(1)); // 누적 1 → sprout(임계 1, spriteId="sprout") 보유

        OwnedPlant sprout = gardenService.view(user).ownedPlants().stream()
                .filter(o -> o.axis() == PlacementAxis.TIME && o.code().equals("sprout"))
                .findFirst().orElseThrow();
        assertThat(sprout.spriteId()).isEqualTo("sprout");
    }

    @Test
    @DisplayName("spriteId 미지정 식물도 ownedPlants에 정상 포함되고 spriteId만 null (폴백 보존·N-055)")
    void spriteId_nullStateDoesNotLeakOut() {
        User user = register("garden-sprite-null@booktimer.com");
        // 누적 3 → sprout(spriteId 있음)·herb(spriteId 미지정) 보유 + 경제책 완독 → 장르 econ(타 축, spriteId 없음)
        metGoalOn(user, today().minusDays(1));
        metGoalOn(user, today().minusDays(2));
        metGoalOn(user, today().minusDays(3));
        registerBook(user, "경제책", "국내도서>경제경영>마케팅", BookStatus.FINISHED);

        var owned = gardenService.view(user).ownedPlants();
        // herb(시간축, spriteId 미지정)이 결과에서 빠지지 않고 spriteId만 null이어야 함(이모지 폴백 보존).
        OwnedPlant herb = owned.stream().filter(o -> o.code().equals("herb")).findFirst().orElseThrow();
        assertThat(herb.spriteId()).isNull();
        // 장르 econ은 spriteId 미지정 픽스처(null-state)라 포함되고 spriteId만 null(이모지 폴백 보존).
        OwnedPlant econ = owned.stream()
                .filter(o -> o.axis() == PlacementAxis.GENRE && o.code().equals("econ"))
                .findFirst().orElseThrow();
        assertThat(econ.spriteId()).isNull();
    }

    // --- A2 후속: 타 축(장르·다양성·레시피) SVG 승격 — spriteId 전파 -----------------------------

    @Test
    @DisplayName("장르축 식물의 spriteId가 ownedPlants 전파에 실린다 (A2 후속)")
    void spriteId_propagatesForGenreAxis() {
        User user = register("garden-sprite-genre@booktimer.com");
        registerBook(user, "소설책", "국내도서>소설/시/희곡>한국소설", BookStatus.FINISHED); // 장르 novel(spriteId="novel") 보유

        OwnedPlant novel = gardenService.view(user).ownedPlants().stream()
                .filter(o -> o.axis() == PlacementAxis.GENRE && o.code().equals("novel"))
                .findFirst().orElseThrow();
        assertThat(novel.spriteId()).isEqualTo("novel");
    }

    @Test
    @DisplayName("다양성축 식물의 spriteId가 ownedPlants 전파에 실린다 (A2 후속)")
    void spriteId_propagatesForDiversityAxis() {
        User user = register("garden-sprite-diversity@booktimer.com");
        // 완독 1권(작가 "저자") → 서로 다른 작가 1명 → author_1(임계 1, spriteId="author_1") 보유.
        registerBook(user, "어떤책", "국내도서>소설/시/희곡>한국소설", BookStatus.FINISHED);

        OwnedPlant author1 = gardenService.view(user).ownedPlants().stream()
                .filter(o -> o.axis() == PlacementAxis.DIVERSITY && o.code().equals("author_1"))
                .findFirst().orElseThrow();
        assertThat(author1.spriteId()).isEqualTo("author_1");
    }

    @Test
    @DisplayName("타 축 spriteId 미지정 식물도 ownedPlants에 정상 포함되고 spriteId만 null (폴백 보존·N-055)")
    void spriteId_nullStateDoesNotLeakOutForOtherAxes() {
        User user = register("garden-sprite-other-null@booktimer.com");
        // 경제책 완독 → 장르 econ(spriteId 미지정) + 작가 author_1(spriteId 있음) 보유.
        registerBook(user, "경제책", "국내도서>경제경영>마케팅", BookStatus.FINISHED);

        var owned = gardenService.view(user).ownedPlants();
        // 장르 econ(spriteId 미지정)이 결과에서 빠지지 않고 spriteId만 null이어야 함.
        OwnedPlant econ = owned.stream()
                .filter(o -> o.axis() == PlacementAxis.GENRE && o.code().equals("econ"))
                .findFirst().orElseThrow();
        assertThat(econ.spriteId()).isNull();
    }
}
