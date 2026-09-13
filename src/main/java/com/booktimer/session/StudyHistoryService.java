package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 공부 기록 — <b>타이머가 잰 측정 사실만</b> 있는 원장이다.
 *
 * <p>판정(지킴/못 지킴)은 한 픽셀도 없다 — 그건 {@link StudyCalendarService}(일정)의 몫이고, 두 화면의
 * 경계가 곧 이 클래스가 따로 사는 이유다. 잔디는 {@link ContributionGraphBuilder}를 <b>그대로 재사용</b>
 * 한다(순수 static이라 독서 의존이 0이다) — 복제하면 「weeks[0]=최신 주」 같은 규약을 두 번 밟게 된다.
 *
 * <p><b>두 범위가 다르다</b>: {@code months}는 전 기간이고 {@code graph}는 빌더가 정하는 53주다.
 * 2년 전 기록은 목록엔 남고 잔디 총합엔 없다 — 잔디가 「최근 1년」을 말하는 그림이기 때문이다.
 */
@Service
@Transactional(readOnly = true)
public class StudyHistoryService {

    /**
     * 잔디 색 농도의 <b>고정 절대 눈금</b>(4시간).
     *
     * <p>공부 목표를 분모로 쓰지 않는다 — 공부 목표엔 변경 이력이 없어(V79) 목표를 올리면 옛 칸이
     * 현재 목표 기준으로 다시 칠해진다(N-059가 독서 잔디에서 버그로 고친 그 현상). 고정 눈금이면
     * ≤1h lv1 · ≤2h lv2 · &lt;4h lv3 · ≥4h lv4로 갈려 「적게~많이」 범례가 그대로 참이다.
     */
    static final long SHADE_SCALE_SECONDS = 4 * 3600L;

    /** @param totalSeconds 그날 완료 세션 합(초) */
    public record Day(LocalDate date, long totalSeconds) {
    }

    /** @param days 그 달의 일자 기록(<b>최신 일 먼저</b>) */
    public record Month(YearMonth month, long totalSeconds, List<Day> days) {
    }

    /**
     * @param graph  최근 53주 잔디(독서와 같은 {@link ContributionGraph} 꼴 — {@code weeks[0]}이 최신 주)
     * @param months 전 기간 월별 묶음(<b>최신 월 먼저</b>). 기록이 없으면 빈 목록.
     */
    public record StudyHistory(ContributionGraph graph, List<Month> months) {
    }

    private final StudySessionRepository sessionRepository;

    public StudyHistoryService(StudySessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 완료 공부 세션을 유저 타임존 일자로 묶어 잔디와 월별 목록을 만든다.
     *
     * <p>진행 중 세션은 쿼리가 제외한다 — 히어로가 매초 더하는 몫은 기록에 없다(독서와 같은 분업).
     * 자정을 걸친 공부는 <b>저장 시 자정에서 조각나</b> 있으므로({@code StudySessionService}) 각 조각이
     * 제 날짜에 들어간다 — 귀속은 여전히 {@code startedAt} 기준이다(달력·히어로와 같은 규칙).
     * 분할 도입 전 레거시 행은 여전히 시작일에 통째로 잡힌다(소급 재분할 없음).
     *
     * @param now 현재 절대 시각 — 유저 타임존의 「오늘」이 잔디의 오른쪽 끝을 정한다
     */
    public StudyHistory history(User user, Instant now) {
        ZoneId zone = ZoneId.of(user.getTimezone());

        // 내림차순이라 아래 월 묶기가 최신 일 먼저로 굳는다(잔디는 순서를 안 본다).
        Map<LocalDate, Long> byDate = new TreeMap<>(Comparator.reverseOrder());
        for (StudySession session : sessionRepository.findByUserAndEndedAtIsNotNull(user)) {
            byDate.merge(LocalDate.ofInstant(session.getStartedAt(), zone), session.getDurationSeconds(), Long::sum);
        }

        ContributionGraph graph = ContributionGraphBuilder.build(
                byDate, LocalDate.ofInstant(now, zone), SHADE_SCALE_SECONDS);

        return new StudyHistory(graph, groupByMonth(byDate));
    }

    /** 최신 일 먼저인 일자 맵을 월별로 묶는다 — 삽입 순서를 보존해 월도 최신 먼저가 된다. */
    private static List<Month> groupByMonth(Map<LocalDate, Long> byDateDesc) {
        Map<YearMonth, List<Day>> byMonth = new LinkedHashMap<>();
        byDateDesc.forEach((date, seconds) ->
                byMonth.computeIfAbsent(YearMonth.from(date), m -> new ArrayList<>()).add(new Day(date, seconds)));
        return byMonth.entrySet().stream()
                .map(e -> new Month(
                        e.getKey(),
                        e.getValue().stream().mapToLong(Day::totalSeconds).sum(),
                        List.copyOf(e.getValue())))
                .toList();
    }
}
