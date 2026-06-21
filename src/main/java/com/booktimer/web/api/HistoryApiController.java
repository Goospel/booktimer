package com.booktimer.web.api;

import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ContributionDay;
import com.booktimer.session.ContributionGraph;
import com.booktimer.session.DayDebt;
import com.booktimer.session.MonthlyReadingSection;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingHistoryService;
import com.booktimer.user.User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * history 페이지 Vue 섬용 JSON API (선별 SPA 단계 1b).
 *
 * <p>기존 {@link com.booktimer.web.HistoryController}가 SSR로 하던 데이터 조회를 JSON으로 반환한다.
 * 서비스 재사용 — 새 도메인 로직 없음. SecurityConfig default-deny로 자동 인증 보호.
 * GrowthStage enum은 클라이언트가 쓸 수 없으므로 {@link GraphDto}에서 emoji·label 문자열로 평탄화.
 */
@RestController
@RequestMapping("/api/history")
public class HistoryApiController {

    private final CurrentUserService currentUserService;
    private final ReadingHistoryService historyService;
    private final ReadingContributionService contributionService;
    private final ReadingDebtService debtService;

    public HistoryApiController(CurrentUserService currentUserService,
                                ReadingHistoryService historyService,
                                ReadingContributionService contributionService,
                                ReadingDebtService debtService) {
        this.currentUserService = currentUserService;
        this.historyService = historyService;
        this.contributionService = contributionService;
        this.debtService = debtService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public HistoryApiResponse history(Principal principal) {
        User user = currentUserService.resolve(principal);
        ContributionGraph graph = contributionService.contributionGraph(user);
        return new HistoryApiResponse(
                user.getNickname(),
                historyService.monthlyHistory(user),
                new GraphDto(
                        graph.weeks(),
                        graph.monthLabels(),
                        graph.totalSeconds(),
                        graph.activeDays(),
                        graph.currentStreak(),
                        graph.growthStage().emoji(),
                        graph.growthStage().label()
                ),
                debtService.weeklyDebt(user).missedDays()
        );
    }

    public record HistoryApiResponse(
            String nickname,
            List<MonthlyReadingSection> months,
            GraphDto graph,
            List<DayDebt> weeklyShortfall) {
    }

    /** ContributionGraph에서 GrowthStage enum을 제거하고 emoji·label 문자열로 평탄화한 DTO. */
    public record GraphDto(
            List<List<ContributionDay>> weeks,
            List<ContributionGraph.MonthLabel> monthLabels,
            long totalSeconds,
            int activeDays,
            int currentStreak,
            String growthEmoji,
            String growthLabel) {
    }
}
