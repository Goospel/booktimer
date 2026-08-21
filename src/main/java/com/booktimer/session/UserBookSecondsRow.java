package com.booktimer.session;

/**
 * 사람·책별 누적 독서 시간(초) — {@link BookSecondsRow}의 <b>여러 사람</b>판 GROUP BY projection.
 *
 * <p>1인용({@code sumSecondsByPublicBook})을 후보 수만큼 부르면 그대로 N+1이라, 둘러보기는 후보 전원을
 * 한 방에 집계하고 {@code userId}로 나눠 쓴다.
 */
public record UserBookSecondsRow(Long userId, Long bookId, Long seconds) {
}
