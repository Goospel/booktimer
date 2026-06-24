package com.booktimer.garden;

import com.booktimer.session.DailyReadingRecord;
import com.booktimer.session.ReadingHistoryService;
import com.booktimer.timer.GoalSchedule;
import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 먹이주기 자원 루프 — EARN·BALANCE·SPEND 세 단계 관리.
 *
 * <ul>
 *   <li>EARN(무저장): 달성일 수({@link DailyQuotaCalculator#metDayCount}) — 독서에서 유도, 위조 불가
 *   <li>BALANCE: {@code foodBalance = earned - spent(≥0)} — 매 요청마다 유도
 *   <li>SPEND(저장): 보유 작가에게 먹이기 → {@link AuthorAffection#feedCount}++ 영속
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class FeedingService {

    private final AuthorAffectionRepository affectionRepository;
    private final ReadingHistoryService readingHistoryService;
    private final ReadingTimerRepository readingTimerRepository;
    private final ReadingGoalChangeRepository readingGoalChangeRepository;
    private final GardenService gardenService;

    public FeedingService(AuthorAffectionRepository affectionRepository,
                          ReadingHistoryService readingHistoryService,
                          ReadingTimerRepository readingTimerRepository,
                          ReadingGoalChangeRepository readingGoalChangeRepository,
                          GardenService gardenService) {
        this.affectionRepository = affectionRepository;
        this.readingHistoryService = readingHistoryService;
        this.readingTimerRepository = readingTimerRepository;
        this.readingGoalChangeRepository = readingGoalChangeRepository;
        this.gardenService = gardenService;
    }

    /**
     * 현재 먹이 잔액 — 달성일 수에서 지금까지 먹인 합을 빼고 0 이상으로 클램프.
     *
     * <p>EARN: {@link DailyQuotaCalculator#metDayCount}로 유도(저장 안 함).
     * SPEND: {@link AuthorAffectionRepository#sumFeedCountByUser}로 조회.
     */
    public int foodBalance(User user) {
        int earned = computeMetDayCount(user);
        long spent = affectionRepository.sumFeedCountByUser(user);
        return (int) Math.max(0L, (long) earned - spent);
    }

    /**
     * 사용자의 캐릭터별 정(affection) — code → feed_count.
     * DB에 행이 없는 캐릭터는 포함되지 않는다(호출자가 0 기본값 처리).
     */
    public Map<String, Integer> affectionByCharacter(User user) {
        return affectionRepository.findByUser(user).stream()
                .collect(Collectors.toMap(
                        AuthorAffection::getCharacterCode,
                        AuthorAffection::getFeedCount));
    }

    /**
     * 보유 작가에게 먹이기 — 먹이 1 소비, 해당 작가 feed_count++.
     *
     * @param user          먹이 행위 주체(IDOR 방지 — 본인 데이터만)
     * @param characterCode 먹일 작가 캐릭터 코드
     * @return 먹이기 후 상태 {@link FeedResult}
     * @throws IllegalArgumentException 미보유 작가이거나 먹이 잔액이 0인 경우
     */
    @Transactional
    public FeedResult feed(User user, String characterCode) {
        // 1. 보유(해금) 작가인지 검증 — IDOR/위조 방어
        GardenView view = gardenService.view(user);
        boolean owned = view.ownedCharacters().stream()
                .anyMatch(c -> c.code().equals(characterCode));
        if (!owned) {
            throw new IllegalArgumentException("보유하지 않은 작가입니다: " + characterCode);
        }

        // 2. 먹이 잔액 확인
        int currentBalance = foodBalance(user);
        if (currentBalance <= 0) {
            throw new IllegalArgumentException("오늘 목표를 채우면 먹이를 얻어요");
        }

        // 3. (user, code) upsert — 있으면 feed_count++, 없으면 feedCount=1로 신규 생성
        Optional<AuthorAffection> opt = affectionRepository.findByUserAndCharacterCode(user, characterCode);
        int beforeFeedCount = opt.map(AuthorAffection::getFeedCount).orElse(0);
        int beforeLevel = AffectionLevel.of(beforeFeedCount).level();

        AuthorAffection affection;
        if (opt.isPresent()) {
            affection = opt.get();
            affection.incrementFeedCount();
        } else {
            affection = AuthorAffection.create(user, characterCode);
        }
        affectionRepository.save(affection);

        // 4. 레벨 계산 + 반환
        AffectionLevel afterLevel = AffectionLevel.of(affection.getFeedCount());
        boolean leveledUp = beforeLevel < afterLevel.level();
        return new FeedResult(
                currentBalance - 1, characterCode, affection.getFeedCount(),
                afterLevel.level(), afterLevel.title(), leveledUp);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────────

    /**
     * 달성일 수 유도 — ReadingHistoryService + GoalSchedule.
     * 가지치기로 삭제된 achievedGoalDates의 핵심 로직 부활.
     */
    private int computeMetDayCount(User user) {
        // 일자별 읽은 초
        List<DailyReadingRecord> history = readingHistoryService.dailyHistory(user);
        Map<LocalDate, Long> secondsByDate = history.stream()
                .collect(Collectors.toMap(DailyReadingRecord::date, DailyReadingRecord::totalSeconds));

        // 목표 스케줄 조립 — N-059 소급 함정 방지
        long currentGoal = readingTimerRepository.findByUser(user)
                .map(t -> t.getDailyIncrementSeconds())
                .orElse(0L);
        Map<LocalDate, Long> changesByDate = readingGoalChangeRepository
                .findByUserOrderByEffectiveDateAsc(user)
                .stream()
                .collect(Collectors.toMap(
                        ReadingGoalChange::getEffectiveDate,
                        ReadingGoalChange::getGoalSeconds));
        GoalSchedule goalSchedule = GoalSchedule.of(changesByDate, currentGoal);

        // baseline: 최초 목표 시점 이전은 "가입 전"으로 제외
        LocalDate baseline = goalSchedule.earliestEffectiveDate().orElse(LocalDate.MIN);

        return DailyQuotaCalculator.metDayCount(secondsByDate, goalSchedule::goalFor, baseline);
    }
}
