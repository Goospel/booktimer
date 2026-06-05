package com.booktimer.web;

import com.booktimer.session.ContributionGraph;
import com.booktimer.session.DailyReadingRecord;
import com.booktimer.session.ReadingContributionService;
import com.booktimer.security.CurrentUserService;
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
 */
@Controller
public class HistoryController {

    private final CurrentUserService currentUserService;
    private final ReadingHistoryService historyService;
    private final ReadingContributionService contributionService;

    public HistoryController(CurrentUserService currentUserService,
                             ReadingHistoryService historyService,
                             ReadingContributionService contributionService) {
        this.currentUserService = currentUserService;
        this.historyService = historyService;
        this.contributionService = contributionService;
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        User user = currentUserService.resolve(principal);

        List<DailyReadingRecord> records = historyService.dailyHistory(user);
        ContributionGraph graph = contributionService.contributionGraph(user);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("records", records);
        model.addAttribute("graph", graph);
        return "history";
    }
}
