package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 일자별 독서 기록 조회 유스케이스 (README 2.2).
 *
 * <p>완료된 측정 세션({@code endedAt != null})을 <b>유저 타임존 기준 일자</b>로 묶어 그날의 총
 * 독서 시간과 세션 수를 집계한다. "어떤 날인지"는 서버 UTC가 아니라 유저가 사는 곳의 자정 경계로
 * 정해져야 하므로 {@link User#getTimezone()}으로 {@link java.time.Instant}를 {@link LocalDate}로 변환한다.
 *
 * <p>MVP 규모에서는 유저의 세션을 메모리에서 묶는다. 데이터가 커지면 DB 집계 쿼리로 옮긴다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingHistoryService {

    private final ReadingSessionRepository sessionRepository;

    public ReadingHistoryService(ReadingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 유저의 완료된 독서 세션을 일자별로 집계해 최신 일자가 먼저 오도록 반환한다.
     *
     * @param user 조회 주체
     * @return 일자별 집계 목록(최신순). 기록이 없으면 빈 목록.
     */
    public List<DailyReadingRecord> dailyHistory(User user) {
        ZoneId zone = ZoneId.of(user.getTimezone());

        // 최신 일자가 먼저 오도록 내림차순 TreeMap에 누적
        Map<LocalDate, long[]> byDate = new TreeMap<>(Comparator.reverseOrder());
        for (ReadingSession session : sessionRepository.findByUser(user)) {
            if (session.isActive()) {
                continue; // 진행 중(미종료) 세션은 집계 제외
            }
            LocalDate date = LocalDate.ofInstant(session.getStartedAt(), zone);
            long[] agg = byDate.computeIfAbsent(date, d -> new long[2]); // [0]=총초, [1]=세션수
            agg[0] += session.getDurationSeconds();
            agg[1] += 1;
        }

        return byDate.entrySet().stream()
                .map(e -> new DailyReadingRecord(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
                .toList();
    }
}
