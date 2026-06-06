package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.session.ReadingSession;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 독서 프로필 집계기 — 책장(book + reading_session)에서 {@link ReadingProfile}(사실)을 뽑는 순수 함수.
 *
 * <p>의도적으로 영속성/스프링에 의존하지 않는다(메모리 리스트만 받음) → DB 없이 경계값을 빠르게 전수 테스트한다.
 * 레포지토리 배선은 {@link ReadingProfileService}가 담당한다.
 */
public final class ReadingProfileAggregator {

    /** 분포(저자·장르)에서 노출할 상위 항목 수 — 압축 프로필이라 상위 몇 개만 LLM에 넘긴다. */
    public static final int TOP_N = 3;

    private ReadingProfileAggregator() {
    }

    /**
     * 주어진 책 목록·세션 목록에서 독서 프로필을 집계한다(결정적).
     *
     * @param books    대상 책들(호출자가 사용자 범위로 이미 거른 것). null이면 빈 목록 취급
     * @param sessions 대상 측정 세션들(같은 사용자). null이면 빈 목록 취급
     */
    public static ReadingProfile aggregate(List<Book> books, List<ReadingSession> sessions) {
        List<Book> safeBooks = books == null ? List.of() : books;
        List<ReadingSession> safeSessions = sessions == null ? List.of() : sessions;

        // ---- 권수·상태 분포 + 저자·장르·연대 분포(저자/장르/연도 없는 책은 해당 분포에서 제외 — N-055) ----
        int total = safeBooks.size();
        int finished = 0;
        int reading = 0;
        int wantToRead = 0;
        Map<String, Integer> authorCounts = new HashMap<>();
        Map<String, Integer> genreCounts = new HashMap<>();
        Map<Integer, Integer> decadeCounts = new HashMap<>();
        for (Book b : safeBooks) {
            switch (b.getStatus()) {
                case FINISHED -> finished++;
                case READING -> reading++;
                case WANT_TO_READ -> wantToRead++;
            }
            String author = blankToNull(b.getAuthor());
            if (author != null) {
                authorCounts.merge(author, 1, Integer::sum);
            }
            String genre = primaryGenre(b.getCategory());
            if (genre != null) {
                genreCounts.merge(genre, 1, Integer::sum);
            }
            Integer decade = decadeOf(b.getPubDate());
            if (decade != null) {
                decadeCounts.merge(decade, 1, Integer::sum);
            }
        }
        double finishedRatio = total == 0 ? 0.0 : round2((double) finished / total);

        // ---- 시간·평균 세션(정독↔다독) — 종료된 세션만 ----
        long totalSeconds = 0L;
        int finishedSessions = 0;
        for (ReadingSession s : safeSessions) {
            if (s.isActive()) {
                continue; // 진행 중(미종료)은 측정 길이 미확정 → 제외
            }
            totalSeconds += s.getDurationSeconds();
            finishedSessions++;
        }
        long avgSession = finishedSessions == 0 ? 0L : totalSeconds / finishedSessions;

        return new ReadingProfile(total, finished, reading, wantToRead, finishedRatio,
                totalSeconds, finishedSessions, avgSession,
                authorCounts.size(), topN(authorCounts),
                genreCounts.size(), topN(genreCounts),
                decadesNewestFirst(decadeCounts));
    }

    /** 소수점 2자리 반올림 — 완독률을 결정적으로 고정(시그니처/단언 안정). */
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 빈/공백 문자열은 null로 — "없음"을 한 표기로 모아 분포에서 일관되게 제외(Book 적재 정규화와 같은 정신). */
    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * 장르 = 알라딘 categoryName의 <b>대분류</b>. categoryName은 "국내도서&gt;소설/시/희곡&gt;한국소설"처럼
     * 경로형인데 1번째 세그먼트("국내도서/외국도서/eBook")는 판매 구분이지 장르가 아니라, <b>2번째 세그먼트</b>를 장르로 쓴다.
     * 세그먼트가 하나뿐이면(구분 없는 단순 카테고리) 그 값을 쓴다. 값이 없으면 null(분포에서 제외).
     * (깊이 정책은 reading-personality-design §8의 열린 질문 — v1은 대분류로 고정.)
     */
    private static String primaryGenre(String category) {
        String normalized = blankToNull(category);
        if (normalized == null) {
            return null;
        }
        String[] parts = normalized.split(">");
        List<String> segments = new java.util.ArrayList<>();
        for (String part : parts) {
            String seg = part.strip();
            if (!seg.isEmpty()) {
                segments.add(seg);
            }
        }
        if (segments.isEmpty()) {
            return null;
        }
        return segments.size() >= 2 ? segments.get(1) : segments.get(0);
    }

    /**
     * pubDate("2020-03-15" 등)의 앞 4자리를 연도로 읽어 연대(연도/10*10)로 환산한다. 앞 4자리가 숫자가 아니거나
     * 출간일이 없으면 null(분포에서 제외) — 적재 견고성을 위해 원문 문자열을 보존했으므로 파싱은 여기서 방어적으로 한다.
     */
    private static Integer decadeOf(String pubDate) {
        String s = blankToNull(pubDate);
        if (s == null || s.length() < 4) {
            return null;
        }
        String year = s.substring(0, 4);
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(year.charAt(i))) {
                return null;
            }
        }
        int y = Integer.parseInt(year);
        return (y / 10) * 10;
    }

    /** 라벨별 권수를 권수 내림차순(동률 시 라벨 오름차순)으로 정렬해 상위 N만 — 결정적. */
    private static List<LabeledCount> topN(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_N)
                .map(e -> new LabeledCount(e.getKey(), e.getValue()))
                .toList();
    }

    /** 출간 연대 분포 — 최신 연대 먼저(내림차순). 신/구 타임라인이라 권수가 아니라 연대로 정렬한다. */
    private static List<LabeledCount> decadesNewestFirst(Map<Integer, Integer> decadeCounts) {
        return decadeCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByKey().reversed())
                .map(e -> new LabeledCount(String.valueOf(e.getKey()), e.getValue()))
                .toList();
    }
}
