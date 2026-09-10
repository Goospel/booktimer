package com.booktimer.session;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루치 독서 기록 집계 (읽기 전용 뷰 모델).
 *
 * <p>같은 날(유저 타임존 기준)의 완료된 측정 세션들을 묶어 <b>총 독서 시간</b>과 그날 읽은
 * <b>책들</b>({@link BookRead})을 담는다. 세션 "횟수"는 일부러 담지 않는다 — 1분도 안 읽고 멈춘 측정까지
 * 세어 숫자가 부풀고 의미가 약하기 때문(사용자 결정 2026-06-05). 대신 "얼마나·무슨 책"을 보여준다.
 *
 * <p><b>{@code totalSeconds} 는 {@code books} 의 합보다 클 수 있다</b> — 책을 안 고르고 잰 세션(레거시)의
 * 시간은 총합에만 들어가고 책 줄에는 안 잡히기 때문이다. 화면은 그 차액을 「책 안 고른 기록」으로 밝혀서
 * 보여준다(조용히 빼면 펼친 시간을 더했을 때 위의 총합과 안 맞는다).
 *
 * @param date           유저 타임존 기준 일자
 * @param totalSeconds   그날의 총 독서 시간(초) — 책 미지정 세션 포함
 * @param books          그날 읽은 책(제목별 합산, <b>오래 읽은 순</b>). 책 미지정 세션만 있으면 빈 목록.
 * @param manuallyFilled 그날 세션 중 <b>수동 입력</b>(빠뜨린 날 직접 채우기)이 하나라도 있으면 true.
 *                       잔디에서 "직접 채운 날"을 테두리로 구분하는 데 쓴다.
 * @param goalSeconds    <b>그 날짜에 유효했던 하루 목표</b>(초) — 기록 화면 하루 막대의 기준이다.
 *                       {@code /api/history} 경로만 채우고({@link ReadingHistoryService#monthlyHistory}),
 *                       그 밖의 경로({@link ReadingHistoryService#dailyHistory} — 잔디·부채·책 상세)는
 *                       <b>0 = 미산정</b>이다(그쪽은 목표를 스스로 해석한다). 0은 화면에서 「목표 없음」과
 *                       같이 다룬다 — 잔디 {@code ContributionGraphBuilder.levelFor}가 목표 0인 날을
 *                       읽었으면 가득으로 치는 것과 같은 규칙.
 */
public record DailyReadingRecord(LocalDate date, long totalSeconds, List<BookRead> books, boolean manuallyFilled,
                                 long goalSeconds) {

    /** 실시간 측정만 있는 날(수동 입력 없음)용 간편 생성자 — {@code manuallyFilled=false}, 목표 미산정. */
    public DailyReadingRecord(LocalDate date, long totalSeconds, List<BookRead> books) {
        this(date, totalSeconds, books, false, 0L);
    }

    /** 목표를 안 싣는 경로(잔디·부채·책 상세)용 — {@code goalSeconds=0}(미산정). */
    public DailyReadingRecord(LocalDate date, long totalSeconds, List<BookRead> books, boolean manuallyFilled) {
        this(date, totalSeconds, books, manuallyFilled, 0L);
    }
}
