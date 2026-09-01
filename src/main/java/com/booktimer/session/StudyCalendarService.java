package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 공부 일정 달력 — <b>수동 체크가 원장이고 측정은 정보</b>라는 관계가 이 클래스의 전부다.
 *
 * <p>서버는 어떤 경로로도 체크를 자동 생성·수정하지 않는다({@link #setCheck}가 유일한 문이고 사용자
 * 요청만 그리로 온다). 달력이 스스로 말하는 것은 「그날 완료 세션이 있었나」까지 — 목표 대비 달성
 * 배지는 그리지 않는다: 공부 목표엔 변경 이력이 없어(V79) 과거를 <b>현재</b> 목표로 판정하게 되고,
 * 목표를 올리면 과거 달성일이 소급 취소되는 거짓이 생긴다.
 */
@Service
@Transactional
public class StudyCalendarService {

    /**
     * 달력 한 칸 — 그날의 측정 합과 판정.
     *
     * @param studiedSeconds 완료 세션 합(초). 체크만 있는 날은 0.
     * @param kept           지킴/못 지킴, {@code null}이면 무기록(행 부재)
     */
    public record CalendarDay(LocalDate date, long studiedSeconds, Boolean kept) {
    }

    private final StudyDailyCheckRepository checkRepository;
    private final StudySessionRepository sessionRepository;

    public StudyCalendarService(StudyDailyCheckRepository checkRepository,
                                StudySessionRepository sessionRepository) {
        this.checkRepository = checkRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 그날의 판정을 남긴다(upsert). {@code kept}가 {@code null}이면 행을 지워 <b>무기록</b>으로 되돌린다 —
     * 화면의 3상태 순환(무기록 → 지킴 → 못 지킴 → 무기록) 마지막 칸이다.
     *
     * <p>같은 날의 행이 있으면 <b>그 행을 갈아 끼운다</b> — 새로 만들면 {@code UNIQUE(user_id, check_date)}
     * 위반이다.
     *
     * <p>과거엔 하한을 두지 않는다(지난달을 나중에 정리하는 것은 정당한 사용). 미래만 막는다.
     *
     * @throws IllegalArgumentException 유저 타임존 기준 오늘을 넘는 날짜 — 메시지가 그대로 400 본문이 된다
     */
    public void setCheck(User user, LocalDate date, Boolean kept, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of(user.getTimezone()));
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("미래 날짜는 체크할 수 없어요");
        }
        Optional<StudyDailyCheck> existing = checkRepository.findByUserAndCheckDate(user, date);
        if (kept == null) {
            existing.ifPresent(checkRepository::delete);
            return;
        }
        StudyDailyCheck row = existing.orElseGet(() -> StudyDailyCheck.of(user, date, kept));
        row.mark(kept);
        checkRepository.save(row);
    }

    /**
     * 그 달의 달력 — <b>데이터가 있는 날만</b> 날짜순으로 준다(희소). 빈 달은 빈 목록이다.
     *
     * <p>월 합산을 DB 함수가 아니라 자바에서 그룹핑하는 이유: 타임존 변환을 SQL로 하면 H2/MySQL 방언이
     * 갈리는데, 한 사람의 한 달치는 아무리 많아도 수십 행이라 옮겨 세는 편이 단순하고 같게 돈다.
     * 귀속 기준은 {@code startedAt}이다 — 히어로의 「오늘 공부한 시간」과 같은 규칙이라야 두 화면이
     * 자정을 걸친 세션을 서로 다른 날에 세지 않는다.
     */
    @Transactional(readOnly = true)
    public List<CalendarDay> month(User user, YearMonth month) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();

        Map<LocalDate, Long> studied = new HashMap<>();
        for (StudySession session : sessionRepository
                .findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        user,
                        first.atStartOfDay(zone).toInstant(),
                        last.plusDays(1).atStartOfDay(zone).toInstant())) {
            studied.merge(LocalDate.ofInstant(session.getStartedAt(), zone), session.getDurationSeconds(), Long::sum);
        }

        Map<LocalDate, Boolean> checks = new HashMap<>();
        for (StudyDailyCheck check : checkRepository.findByUserAndCheckDateBetween(user, first, last)) {
            checks.put(check.getCheckDate(), check.isKept());
        }

        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate date : new TreeSet<>(union(studied, checks))) {
            days.add(new CalendarDay(date, studied.getOrDefault(date, 0L), checks.get(date)));
        }
        return days;
    }

    private static List<LocalDate> union(Map<LocalDate, Long> studied, Map<LocalDate, Boolean> checks) {
        List<LocalDate> all = new ArrayList<>(studied.keySet());
        all.addAll(checks.keySet());
        return all;
    }
}
