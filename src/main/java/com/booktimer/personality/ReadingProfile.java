package com.booktimer.personality;

import java.util.List;

/**
 * 독서 프로필 — 한 사용자의 책장에서 코드가 결정적으로 집계한 "사실"(책BTI 입력).
 *
 * <p>책BTI(독서 성향 분석)의 1단계 산출물. 여기엔 <b>해석이 없다</b> — 숫자/분포만 담는다.
 * "이 사람은 정독형이다" 같은 서술은 다음 단계(LLM)가 이 사실을 보고 만든다(reading-personality-design §2).
 * raw 행을 LLM에 통째로 던지지 않으려고 압축한 입력이기도 하다(토큰↓·환각↓).
 *
 * <p>모든 값은 결정적이다 — 같은 책장이면 같은 프로필이 나온다(분포 정렬에 동률 시 라벨 오름차순 타이브레이크).
 * 이는 Phase 4의 입력 시그니처(재생성 트리거)와 테스트 단언을 안정시킨다.
 *
 * <p>v1은 <b>본인용·전체 책 기반</b>이다(공개 여부 무관). 공개·매칭용(PUBLIC 책만) 프로필은 후속 단계에서
 * 별도 생성한다(reading-personality-design §7).
 *
 * @param totalBooks          보유 권수(전체)
 * @param finishedBooks       완독(FINISHED) 권수
 * @param readingBooks        읽는중(READING) 권수
 * @param wantToReadBooks     읽고싶음(WANT_TO_READ) 권수
 * @param finishedRatio       완독률 = finishedBooks/totalBooks(소수점 2자리 반올림). 0권이면 0.0(0 나눗셈 회피)
 * @param totalReadingSeconds 총 독서 시간(초) — 종료된 측정 세션 길이의 합(진행 중 세션 제외)
 * @param finishedSessionCount 종료된 측정 세션 수(평균 세션 길이의 분모)
 * @param avgSessionSeconds   평균 세션 길이(초) = totalReadingSeconds/finishedSessionCount(정수 내림). 세션 0이면 0.
 *                            길수록 정독, 권수 많을수록 다독 — 둘을 함께 보면 정독↔다독 신호
 * @param distinctAuthors     서로 다른 저자 수(저자 비거나 null인 책 제외 — 다양성 신호)
 * @param topAuthors          최다독 저자 상위 N(권수 내림차순, 동률 시 이름 오름차순). 저자 없는 책 제외
 * @param distinctGenres      서로 다른 장르 수(장르 null인 책 제외 — 편식↔잡식 신호)
 * @param topGenres           상위 장르 분포 N(권수 내림차순, 동률 시 라벨 오름차순). 장르 = categoryName 대분류
 * @param pubDecades          출간 연대 분포(연대 내림차순=최신 먼저). pubDate 없거나 연도 못 읽는 책 제외 — 신/구 신호
 */
public record ReadingProfile(
        int totalBooks,
        int finishedBooks,
        int readingBooks,
        int wantToReadBooks,
        double finishedRatio,
        long totalReadingSeconds,
        int finishedSessionCount,
        long avgSessionSeconds,
        int distinctAuthors,
        List<LabeledCount> topAuthors,
        int distinctGenres,
        List<LabeledCount> topGenres,
        List<LabeledCount> pubDecades) {
}
