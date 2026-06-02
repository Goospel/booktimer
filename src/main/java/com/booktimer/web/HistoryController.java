package com.booktimer.web;

import com.booktimer.session.DailyReadingRecord;
import com.booktimer.session.ReadingHistoryService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

/**
 * 일자별 독서 기록 조회 화면 (README 2.2).
 *
 * <p>인증 주체(username=email)로 도메인 {@link User}를 찾아, 완료된 측정 세션을 유저 타임존 기준
 * 일자별로 집계({@link ReadingHistoryService})해 화면에 싣는다. MVP에서는 "어떤 책"이 아니라
 * <b>시간만</b> 보여준다.
 */
@Controller
public class HistoryController {

    private final UserRepository userRepository;
    private final ReadingHistoryService historyService;

    public HistoryController(UserRepository userRepository, ReadingHistoryService historyService) {
        this.userRepository = userRepository;
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));

        List<DailyReadingRecord> records = historyService.dailyHistory(user);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("records", records);
        return "history";
    }
}
