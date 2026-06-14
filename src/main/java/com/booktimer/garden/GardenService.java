package com.booktimer.garden;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.DailyReadingRecord;
import com.booktimer.session.ReadingHistoryService;
import com.booktimer.timer.GoalSchedule;
import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 독서 정원(트랙 A) 조회 유스케이스 — 보유 식물을 저장하지 않고 독서 실적에서 유도한다(부채 모델 N-058).
 *
 * <p>두 수집축을 한 화면에 조립한다:
 * <ul>
 *   <li><b>시간축</b>({@link Plant}) — 잔디({@code ReadingContributionService})와 같은 입력(일자별 집계·
 *       목표 변경 이력)으로 "그날 목표를 채운 날"의 누적 수로 식물을 해금한다. 목표를 올려도 옛 목표를 채운
 *       과거 날이 소급 박탈되지 않게 날짜별 목표로 판정하고(N-059), baseline 이전(가입 전) 날은 제외한다.
 *       "오늘"은 유저 타임존 자정 경계(N-010).</li>
 *   <li><b>장르축</b>({@link GenrePlant}) — 완독(FINISHED) 책의 장르 대분류를 모아 그 장르 식물을 보유로
 *       친다. 완독만 집계해 파밍을 막고 책BTI와 신호를 일치시킨다(설계 §2.3).</li>
 *   <li><b>다양성축</b>({@link DiversityPlant}) — 완독 책의 <b>서로 다른(distinct) 작가/출판사 수</b>가 임계를
 *       넘으면 해당 식물을 보유로 친다. 작가·출판사가 같은 카운트 메커닉이라 한 카탈로그에 {@code kind}로
 *       담는다(설계 §2.2). 장르축과 같이 완독만 집계한다.</li>
 *   <li><b>레시피축(트랙 B)</b>({@link RecipePlant}) — 완독+읽고싶음 책의 <b>조합</b>을 발견하면 얻는다.
 *       시간축·장르축과 달리 발견을 <b>저장</b>한다({@link UserDiscoveredPlant}) — 발견은 이벤트라 박아야
 *       하고 책장이 바뀌어도 보유가 유지돼야 하기 때문(설계 §2.2). 그래서 조회 시 평가→저장이 일어난다.</li>
 * </ul>
 *
 * <p>순수 계산은 {@link PlantUnlockCalculator}·{@link GenreUnlockCalculator}·{@link DiversityUnlockCalculator}·
 * {@link RecipeEvaluator}에 위임해 단위테스트로 전수 검증된다. 여기선 소스 배선(일자 집계·목표 이력·책장 조회·
 * 카탈로그 조회)과 조립, 그리고 트랙 B 발견 저장만 한다.
 *
 * <p><b>트랜잭션</b>: 트랙 A는 읽기지만 트랙 B는 발견을 저장(쓰기)한다. 조회가 쓰기를 유발하는 패턴이라
 * (설계 §2.1 옵션 B — 대시보드 조회 시 평가) {@link #view}를 읽기-쓰기 트랜잭션으로 둔다. 중복 발견은
 * {@code uk(user_id, plant_code)}와 {@code existsByUserAndPlantCode} 가드로 막는다(멱등).
 */
@Service
@Transactional(readOnly = true)
public class GardenService {

    /** 타이머가 없을 때의 기본 하루 목표(초) — {@code ReadingContributionService}와 동일(1시간). */
    static final long DEFAULT_GOAL_SECONDS = 3600L;

    /** 해금 후 며칠까지 "NEW"로 볼지. */
    static final int NEW_WINDOW_DAYS = 7;

    private final PlantRepository plantRepository;
    private final GenrePlantRepository genrePlantRepository;
    private final DiversityPlantRepository diversityPlantRepository;
    private final RecipePlantRepository recipePlantRepository;
    private final UserDiscoveredPlantRepository discoveredPlantRepository;
    private final BookRepository bookRepository;
    private final ReadingHistoryService historyService;
    private final ReadingTimerRepository timerRepository;
    private final ReadingGoalChangeRepository goalChangeRepository;
    private final Clock clock;

    public GardenService(PlantRepository plantRepository,
                         GenrePlantRepository genrePlantRepository,
                         DiversityPlantRepository diversityPlantRepository,
                         RecipePlantRepository recipePlantRepository,
                         UserDiscoveredPlantRepository discoveredPlantRepository,
                         BookRepository bookRepository,
                         ReadingHistoryService historyService,
                         ReadingTimerRepository timerRepository,
                         ReadingGoalChangeRepository goalChangeRepository,
                         Clock clock) {
        this.plantRepository = plantRepository;
        this.genrePlantRepository = genrePlantRepository;
        this.diversityPlantRepository = diversityPlantRepository;
        this.recipePlantRepository = recipePlantRepository;
        this.discoveredPlantRepository = discoveredPlantRepository;
        this.bookRepository = bookRepository;
        this.historyService = historyService;
        this.timerRepository = timerRepository;
        this.goalChangeRepository = goalChangeRepository;
        this.clock = clock;
    }

    // 트랙 B 발견을 저장하므로 읽기-쓰기 트랜잭션(클래스 기본 readOnly=true를 메서드에서 덮어쓴다).
    @Transactional
    public GardenView view(User user) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        // 1) 일자별 총 독서초 (잔디와 동일 소스)
        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        for (DailyReadingRecord record : historyService.dailyHistory(user)) {
            secondsByDate.put(record.date(), record.totalSeconds());
        }

        // 2) 그날 유효했던 목표 — 변경 이력 → GoalSchedule (ReadingContributionService와 동일 조립)
        long currentGoalSeconds = timerRepository.findByUser(user)
                .map(timer -> timer.getDailyIncrementSeconds())
                .orElse(DEFAULT_GOAL_SECONDS);
        Map<LocalDate, Long> changesByDate = new LinkedHashMap<>();
        for (ReadingGoalChange change : goalChangeRepository.findByUserOrderByEffectiveDateAsc(user)) {
            changesByDate.put(change.getEffectiveDate(), change.getGoalSeconds());
        }
        GoalSchedule schedule = GoalSchedule.of(changesByDate, currentGoalSeconds);

        // 3) 그날 목표를 채운 날들(오름차순) → 누적 달성일
        List<LocalDate> achieved = PlantUnlockCalculator.achievedGoalDates(
                secondsByDate, schedule::goalFor, schedule.earliestEffectiveDate().orElse(null));
        int achievedDays = achieved.size();

        // 4) 카탈로그(임계 오름차순) → 각 식물 상태 조립
        List<Plant> catalog = plantRepository.findAllByOrderByUnlockThresholdDaysAscDisplayOrderAsc();
        List<PlantState> states = new ArrayList<>(catalog.size());
        int ownedCount = 0;
        Integer daysToNextUnlock = null;
        String nextPlantName = null;
        for (Plant plant : catalog) {
            long threshold = plant.getUnlockThresholdDays();
            boolean owned = threshold >= 1 && threshold <= achievedDays;
            LocalDate unlockedOn = owned ? achieved.get((int) threshold - 1) : null;
            boolean isNew = owned && !unlockedOn.isBefore(today.minusDays(NEW_WINDOW_DAYS));
            states.add(new PlantState(plant, owned, unlockedOn, isNew));
            if (owned) {
                ownedCount++;
            } else if (daysToNextUnlock == null) {
                // 카탈로그가 임계 오름차순이라 첫 미보유 = 다음 해금 대상
                daysToNextUnlock = (int) (threshold - achievedDays);
                nextPlantName = plant.getName();
            }
        }

        // 책장은 트랙 A 장르축·트랙 B 레시피축이 함께 쓰므로 한 번만 조회한다.
        List<Book> books = bookRepository.findByUserOrderByCreatedAtDesc(user);

        // 5) 장르축 — 완독책의 장르 대분류로 장르 식물 보유 유도(완독만, 파밍 방지·N-055 null 제외)
        List<String> finishedCategories = books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .map(Book::getCategory)
                .toList();
        Set<String> ownedGenres = GenreUnlockCalculator.achievedGenres(finishedCategories);
        List<GenrePlant> genreCatalog = genrePlantRepository.findAllByOrderByDisplayOrderAsc();
        List<GenrePlantState> genrePlants = GenreUnlockCalculator.resolve(genreCatalog, ownedGenres);
        int ownedGenreCount = (int) genrePlants.stream().filter(GenrePlantState::owned).count();

        // 5.5) 다양성축 — 완독책의 distinct 작가/출판사 수로 작가·출판사 식물 보유 유도(완독만, 파밍 방지).
        // author/publisher는 적재 시 정규화되지 않으므로(category와 달리 blankToNull 없음) distinctCount가
        // strip + 빈/null 제외를 맡는다(미완성 메타 누수 가드 N-055). 책장 추가 조회 없이 위 books를 재사용.
        List<String> finishedAuthors = books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .map(Book::getAuthor)
                .toList();
        List<String> finishedPublishers = books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .map(Book::getPublisher)
                .toList();
        int authorCount = DiversityUnlockCalculator.distinctCount(finishedAuthors);
        int publisherCount = DiversityUnlockCalculator.distinctCount(finishedPublishers);
        List<DiversityPlant> diversityCatalog = diversityPlantRepository.findAllByOrderByDisplayOrderAsc();
        List<DiversityPlantState> diversityPlants =
                DiversityUnlockCalculator.resolve(diversityCatalog, authorCount, publisherCount);
        int ownedAuthorCount = (int) diversityPlants.stream()
                .filter(s -> s.plant().getKind() == DiversityKind.AUTHOR && s.owned()).count();
        int totalAuthorCount = (int) diversityCatalog.stream()
                .filter(p -> p.getKind() == DiversityKind.AUTHOR).count();
        int ownedPublisherCount = (int) diversityPlants.stream()
                .filter(s -> s.plant().getKind() == DiversityKind.PUBLISHER && s.owned()).count();
        int totalPublisherCount = (int) diversityCatalog.stream()
                .filter(p -> p.getKind() == DiversityKind.PUBLISHER).count();

        // 6) 레시피축(트랙 B) — 책장 스냅샷으로 만족된 레시피를 평가하고, 새로 발견한 것을 저장한다.
        ShelfSnapshot snapshot = shelfSnapshot(books);
        Set<String> satisfied = RecipeEvaluator.satisfiedPlantCodes(snapshot);
        Set<String> alreadyDiscovered = discoveredPlantRepository.findByUser(user).stream()
                .map(UserDiscoveredPlant::getPlantCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> newlyDiscovered = new LinkedHashSet<>();
        Instant now = clock.instant();
        for (String code : satisfied) {
            // existsBy 가드 + uk 제약으로 중복 발견을 막는다(멱등·동시성 안전망).
            if (!alreadyDiscovered.contains(code)
                    && !discoveredPlantRepository.existsByUserAndPlantCode(user, code)) {
                discoveredPlantRepository.save(UserDiscoveredPlant.of(user, code, now));
                newlyDiscovered.add(code);
            }
        }
        Set<String> allDiscovered = new LinkedHashSet<>(alreadyDiscovered);
        allDiscovered.addAll(newlyDiscovered);

        List<RecipePlant> recipeCatalog = recipePlantRepository.findAllByOrderByDisplayOrderAsc();
        List<DiscoveredPlantState> recipePlants = recipeCatalog.stream()
                .map(rp -> new DiscoveredPlantState(rp, allDiscovered.contains(rp.getCode())))
                .toList();
        int discoveredCount = (int) recipePlants.stream().filter(DiscoveredPlantState::discovered).count();
        int mysterySlotCount = recipeCatalog.size() - discoveredCount;
        // 토스트용 — 이번에 새로 발견된 식물 이름(진열 순서, 카탈로그에 있는 것만).
        List<String> justDiscovered = recipeCatalog.stream()
                .filter(rp -> newlyDiscovered.contains(rp.getCode()))
                .map(RecipePlant::getName)
                .toList();

        return new GardenView(states, ownedCount, catalog.size(), achievedDays, daysToNextUnlock, nextPlantName,
                genrePlants, ownedGenreCount, genreCatalog.size(),
                diversityPlants, ownedAuthorCount, totalAuthorCount, ownedPublisherCount, totalPublisherCount,
                recipePlants, mysterySlotCount, justDiscovered);
    }

    /** 책장을 트랙 B 레시피 평가 입력으로 집계한다 — 완독·읽고싶음 책의 작가·카테고리만 뽑는다. */
    private static ShelfSnapshot shelfSnapshot(List<Book> books) {
        List<ShelfSnapshot.BookMeta> finished = new ArrayList<>();
        List<ShelfSnapshot.BookMeta> wantToRead = new ArrayList<>();
        for (Book book : books) {
            ShelfSnapshot.BookMeta meta = new ShelfSnapshot.BookMeta(book.getAuthor(), book.getCategory());
            if (book.getStatus() == BookStatus.FINISHED) {
                finished.add(meta);
            } else if (book.getStatus() == BookStatus.WANT_TO_READ) {
                wantToRead.add(meta);
            }
        }
        return ShelfSnapshot.of(finished, wantToRead);
    }
}
