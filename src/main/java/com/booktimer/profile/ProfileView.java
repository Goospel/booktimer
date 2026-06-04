package com.booktimer.profile;

import com.booktimer.book.Book;
import com.booktimer.session.ContributionGraph;

import java.util.List;
import java.util.Map;

/**
 * 공개 프로필 화면 모델 (SNS 2단계, sns-design §7.2).
 *
 * <p>"남에게 보이는 공개 프로필"이라 <b>전부 PUBLIC 범위로 거른 값</b>만 담는다 — PRIVATE 책·세션은
 * 애초에 들어오지 않는다(필드 누락이 아니라 설계적 차단, §3.4). 이메일·타이머 내부값 등은 담지 않는다.
 *
 * @param nickname  대상 사용자 닉네임(공개 핸들)
 * @param books     공개(PUBLIC) 책 목록(최신 등록 먼저)
 * @param bookTimes 책 id → 누적 독서 초(공개 책만)
 * @param graph     공개 잔디(PUBLIC 책 세션만 반영)
 */
public record ProfileView(
        String nickname,
        List<Book> books,
        Map<Long, Long> bookTimes,
        ContributionGraph graph) {
}
