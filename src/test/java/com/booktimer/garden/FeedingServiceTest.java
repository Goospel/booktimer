package com.booktimer.garden;

import com.booktimer.session.DailyReadingRecord;
import com.booktimer.session.ReadingHistoryService;
import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FeedingService 단위 테스트 (Mockito 모킹).
 *
 * <p>foodBalance 산식·음수 클램프, feed 보유/미보유·먹이 유무 분기,
 * 같은 작가 두 번 먹이기를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FeedingServiceTest {

    private static final LocalDate BASELINE = LocalDate.of(2026, 1, 1);
    private static final long GOAL = 3600L;

    @Mock private AuthorAffectionRepository affectionRepository;
    @Mock private ReadingHistoryService readingHistoryService;
    @Mock private ReadingTimerRepository readingTimerRepository;
    @Mock private ReadingGoalChangeRepository readingGoalChangeRepository;
    @Mock private GardenService gardenService;

    @InjectMocks
    private FeedingService feedingService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("test@booktimer.com", "hash", "독자", "Asia/Seoul", Role.USER);
    }

    // ── foodBalance ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("foodBalance = 달성일(3) − 먹인합(1) = 2")
    void foodBalance_metMinusSpent() {
        stubHistory(3);
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(1L);

        assertThat(feedingService.foodBalance(user)).isEqualTo(2);
    }

    @Test
    @DisplayName("foodBalance 음수 → 0으로 클램프 (방어)")
    void foodBalance_negativeClampedToZero() {
        stubHistory(1);
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(5L);

        assertThat(feedingService.foodBalance(user)).isEqualTo(0);
    }

    @Test
    @DisplayName("달성일 0 → foodBalance 0")
    void foodBalance_zeroDays_zero() {
        stubHistory(0);
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(0L);

        assertThat(feedingService.foodBalance(user)).isEqualTo(0);
    }

    // ── feed ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("보유 작가 + 먹이 > 0 → feed_count=1, foodBalance−1 반환")
    void feed_ownedCharacter_success() {
        stubHistory(2); // earned=2
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(0L); // spent=0
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));
        when(affectionRepository.findByUserAndCharacterCode(user, "han_gang"))
                .thenReturn(Optional.empty()); // 최초 먹이기
        AuthorAffection newAffection = AuthorAffection.create(user, "han_gang"); // feedCount=1
        when(affectionRepository.save(any())).thenReturn(newAffection);

        FeedResult result = feedingService.feed(user, "han_gang");

        assertThat(result.characterCode()).isEqualTo("han_gang");
        assertThat(result.affection()).isEqualTo(1);
        assertThat(result.foodBalance()).isEqualTo(1); // 2-1=1
        verify(affectionRepository).save(any(AuthorAffection.class));
    }

    @Test
    @DisplayName("미보유 작가 → IllegalArgumentException (위조 방어)")
    void feed_unownedCharacter_throws() {
        // 보유 검증은 gardenService.view() 직후 → foodBalance()는 호출되지 않음
        when(gardenService.view(user)).thenReturn(viewWith()); // 보유 작가 없음

        assertThatThrownBy(() -> feedingService.feed(user, "han_gang"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("먹이 잔액 0 → IllegalArgumentException (오늘 목표 달성 유도)")
    void feed_noFood_throws() {
        stubHistory(0); // earned=0, balance=0
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(0L);
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));

        assertThatThrownBy(() -> feedingService.feed(user, "han_gang"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("목표");
    }

    @Test
    @DisplayName("같은 작가 두 번 먹이기 → 두 번째 affection=2")
    void feed_twiceSameCharacter_affection2() {
        stubHistory(5); // earned=5, 먹이 충분

        // 두 번째 피드 시나리오: DB에 이미 feedCount=1인 행이 있는 상태
        // feed()가 findByUserAndCharacterCode → incrementFeedCount() → feedCount=2
        AuthorAffection existing = AuthorAffection.create(user, "han_gang"); // feedCount=1 (첫 피드 후 상태)
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(1L); // 이미 1번 먹임
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));
        when(affectionRepository.findByUserAndCharacterCode(user, "han_gang"))
                .thenReturn(Optional.of(existing));
        when(affectionRepository.save(any())).thenReturn(existing);

        FeedResult result = feedingService.feed(user, "han_gang");

        // feed()가 existing.incrementFeedCount() 호출 → feedCount=2
        assertThat(result.affection()).isEqualTo(2);
    }

    // ── level · title · leveledUp ─────────────────────────────────────────────────

    @Test
    @DisplayName("feed: count 2→3(Lv1→Lv2) → level=2·title=친해지는중·leveledUp=true")
    void feed_returns_levelAndTitle() {
        // 기존 feedCount=2(Lv1), 먹이 후 feedCount=3(Lv2) → 레벨업
        stubHistory(5);
        AuthorAffection existing = AuthorAffection.create(user, "han_gang"); // feedCount=1
        existing.incrementFeedCount();                                         // feedCount=2
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(2L);
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));
        when(affectionRepository.findByUserAndCharacterCode(user, "han_gang"))
                .thenReturn(Optional.of(existing));
        when(affectionRepository.save(any())).thenReturn(existing);

        FeedResult result = feedingService.feed(user, "han_gang");

        assertThat(result.level()).isEqualTo(2);
        assertThat(result.title()).isEqualTo("친해지는 중");
        assertThat(result.leveledUp()).isTrue();
    }

    @Test
    @DisplayName("leveledUp: 신규(0→1) = true")
    void feed_leveledUp_newCharacter_true() {
        stubHistory(2);
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(0L);
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));
        when(affectionRepository.findByUserAndCharacterCode(user, "han_gang"))
                .thenReturn(Optional.empty());
        when(affectionRepository.save(any())).thenReturn(AuthorAffection.create(user, "han_gang"));

        FeedResult result = feedingService.feed(user, "han_gang");

        assertThat(result.leveledUp()).isTrue(); // Lv0→Lv1
    }

    @Test
    @DisplayName("leveledUp: count 3→4 (Lv2 내) = false")
    void feed_leveledUp_sameLevel_false() {
        // feedCount=3(Lv2), 먹이 후 feedCount=4(Lv2) — 레벨 안 오름
        stubHistory(5);
        AuthorAffection existing = AuthorAffection.create(user, "han_gang"); // feedCount=1
        existing.incrementFeedCount();
        existing.incrementFeedCount(); // feedCount=3
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(3L);
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));
        when(affectionRepository.findByUserAndCharacterCode(user, "han_gang"))
                .thenReturn(Optional.of(existing));
        when(affectionRepository.save(any())).thenReturn(existing);

        FeedResult result = feedingService.feed(user, "han_gang");

        assertThat(result.leveledUp()).isFalse();
    }

    @Test
    @DisplayName("leveledUp: 최대 레벨(Lv5, count 30+)에서 더 먹임 = false")
    void feed_leveledUp_maxLevel_false() {
        // feedCount=30(Lv5), 먹이 후 feedCount=31(Lv5) — 최대 레벨 유지
        stubHistory(50);
        AuthorAffection existing = AuthorAffection.create(user, "han_gang"); // feedCount=1
        for (int i = 1; i < 30; i++) existing.incrementFeedCount();          // feedCount=30
        when(affectionRepository.sumFeedCountByUser(user)).thenReturn(30L);
        when(gardenService.view(user)).thenReturn(viewWith("han_gang"));
        when(affectionRepository.findByUserAndCharacterCode(user, "han_gang"))
                .thenReturn(Optional.of(existing));
        when(affectionRepository.save(any())).thenReturn(existing);

        FeedResult result = feedingService.feed(user, "han_gang");

        assertThat(result.leveledUp()).isFalse();
        assertThat(result.level()).isEqualTo(5);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** N일 달성 이력으로 스텁 — GoalSchedule baseline은 BASELINE */
    private void stubHistory(int metDays) {
        List<DailyReadingRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < metDays; i++) {
            records.add(new DailyReadingRecord(BASELINE.plusDays(i), GOAL, List.of()));
        }
        when(readingHistoryService.dailyHistory(user)).thenReturn(records);
        when(readingTimerRepository.findByUser(user))
                .thenReturn(Optional.of(ReadingTimer.of(GOAL)));
        when(readingGoalChangeRepository.findByUserOrderByEffectiveDateAsc(user))
                .thenReturn(List.of(ReadingGoalChange.of(user, BASELINE, GOAL)));
    }

    /** 주어진 code들을 보유한 GardenView 생성 (같은 패키지라 package-private 접근 가능) */
    private static GardenView viewWith(String... codes) {
        List<AuthorCharacterState> states = new java.util.ArrayList<>();
        for (String code : codes) {
            AuthorCharacter ch = AuthorCharacter.of(code, code, code, "🪶", 1, null);
            states.add(new AuthorCharacterState(ch, true));
        }
        return new GardenView(states, codes.length, codes.length, List.of(), 0, 0);
    }
}
