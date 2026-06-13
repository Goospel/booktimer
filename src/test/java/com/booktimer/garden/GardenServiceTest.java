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
    private BookRepository bookRepository;
    @Autowired
    private GardenService gardenService;
    @Autowired
    private Clock clock;

    // 테스트 카탈로그 — 운영 시드(V35·V36)와 디커플해 배선만 본다.
    // Flyway는 테스트에서 꺼져 있어(application.properties) 시드가 안 도므로 직접 심는다.
    @BeforeEach
    void seedCatalog() {
        plantRepository.save(Plant.of("sprout", "새싹", "🌱", 1, 1));
        plantRepository.save(Plant.of("herb", "허브", "🌿", 2, 3));
        plantRepository.save(Plant.of("clover", "클로버", "☘️", 3, 5));
        plantRepository.save(Plant.of("pot", "화분 모종", "🪴", 4, 7));

        genrePlantRepository.save(GenrePlant.of("novel", "소설/시/희곡", "소설나무", "🌳", 1));
        genrePlantRepository.save(GenrePlant.of("econ", "경제경영", "경제선인장", "🌵", 2));
        genrePlantRepository.save(GenrePlant.of("wildflower", null, "이름 모를 들꽃", "🌼", 99));
    }

    /** 주어진 장르(category)·상태로 책 한 권을 등록한다. */
    private void registerBook(User user, String title, String category, BookStatus status) {
        bookRepository.save(Book.register(user, title, "저자", null, null, null, null,
                category, null, status));
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
}
