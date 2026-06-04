package com.booktimer.session;

import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 독서 잔디(컨트리뷰션 그래프) 조회 유스케이스.
 *
 * <p>일자별 집계({@link ReadingHistoryService})를 GitHub 잔디 형태의 1년치 그리드로 펼친다.
 * "오늘"은 서버 UTC가 아니라 <b>유저 타임존</b> 자정 경계로 정해야 하므로 {@link Clock} +
 * {@link User#getTimezone()}으로 계산한다(N-010). 그리드 구성 자체는 순수 빌더
 * {@link ContributionGraphBuilder}에 위임해 단위테스트로 검증된다.
 *
 * <p>색 농도는 유저의 <b>하루 목표</b>(타이머의 평면 증가값 {@code dailyIncrementSeconds}) 대비 달성 비율로 정한다
 * — 그래서 타이머를 함께 조회해 목표를 빌더에 넘긴다. 타이머가 없으면(불변식상 거의 없음) 기본 목표로 폴백한다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingContributionService {

    /** 타이머가 없을 때의 기본 하루 목표(초) — 가입 시 기본 증가값과 동일(1시간). */
    static final long DEFAULT_GOAL_SECONDS = 3600L;

    private final ReadingHistoryService historyService;
    private final ReadingTimerRepository timerRepository;
    private final Clock clock;

    public ReadingContributionService(ReadingHistoryService historyService,
                                      ReadingTimerRepository timerRepository,
                                      Clock clock) {
        this.historyService = historyService;
        this.timerRepository = timerRepository;
        this.clock = clock;
    }

    /** 본인이 보는 전체 잔디 — 모든 완료 세션을 합산한다. */
    public ContributionGraph contributionGraph(User user) {
        return graphFrom(user, historyService.dailyHistory(user));
    }

    /**
     * 타인 프로필(SNS)에서 보이는 <b>공개 잔디</b> — PUBLIC 책 세션만 합산한다(sns-design §3.5).
     * 색 농도 목표·타임존은 대상 사용자 기준(자기 잔디를 자기 기준으로 본다).
     */
    public ContributionGraph publicContributionGraph(User user) {
        return graphFrom(user, historyService.publicDailyHistory(user));
    }

    /** 일자별 집계를 받아 유저 타임존 "오늘"·하루 목표로 잔디 그리드를 만든다(가시성 필터는 호출자가 끝낸 상태). */
    private ContributionGraph graphFrom(User user, List<DailyReadingRecord> history) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        for (DailyReadingRecord record : history) {
            secondsByDate.put(record.date(), record.totalSeconds());
        }

        long goalSeconds = timerRepository.findByUser(user)
                .map(timer -> timer.getDailyIncrementSeconds())
                .orElse(DEFAULT_GOAL_SECONDS);

        return ContributionGraphBuilder.build(secondsByDate, today, goalSeconds);
    }
}
