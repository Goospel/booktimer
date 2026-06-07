package com.booktimer.web;

import com.booktimer.session.ContributionGraph;
import com.booktimer.session.DailyReadingRecord;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.ReadingDebtService;
import com.booktimer.session.ReadingHistoryService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

/**
 * 일자별 독서 기록 조회 화면 (README 2.2).
 *
 * <p>인증 주체(username=email)로 도메인 {@link User}를 찾아, 완료된 측정 세션을 유저 타임존 기준
 * 일자별로 집계({@link ReadingHistoryService})해 화면에 싣는다. 하루치 행은 <b>총 독서 시간</b>과
 * 그날 <b>읽은 책 제목</b>을 보여준다(세션 "횟수"는 1분 미만 측정까지 부풀려 빼기로 결정, 2026-06-05).
 *
 * <p>"이번 주 빠뜨린 날"(7일 윈도우 내 목표 미충족 과거 날, {@link ReadingDebtService})도 함께 싣는다 —
 * 원래 대시보드에 있었으나 독서 기록 화면으로 옮겼다(채워 넣기=과거 기록 보정이라 기록 화면이 더 자연스러운
 * 자리). 대시보드는 헤드라인(오늘 부채)만 남긴다.
 */
@Controller
public class HistoryController {

    private final CurrentUserService currentUserService;
    private final ReadingHistoryService historyService;
    private final ReadingContributionService contributionService;
    private final ReadingDebtService debtService;

    public HistoryController(CurrentUserService currentUserService,
                             ReadingHistoryService historyService,
                             ReadingContributionService contributionService,
                             ReadingDebtService debtService) {
        this.currentUserService = currentUserService;
        this.historyService = historyService;
        this.contributionService = contributionService;
        this.debtService = debtService;
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);

        List<DailyReadingRecord> records = historyService.dailyHistory(user);
        ContributionGraph graph = contributionService.contributionGraph(user);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("records", records);
        model.addAttribute("graph", graph);
        // 이번 주 빠뜨린 날(최근 7일 윈도우 내 부채>0 과거 날, 최근 먼저). 각 항목 "채우기"=수동입력 날짜 프리필.
        model.addAttribute("weeklyShortfall", debtService.weeklyDebt(user).missedDays());
        return "history";
    }
}
