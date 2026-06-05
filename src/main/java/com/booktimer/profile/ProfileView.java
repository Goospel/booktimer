package com.booktimer.profile;

import com.booktimer.book.Book;
import com.booktimer.session.ContributionGraph;

import java.util.List;
import java.util.Map;

/**
 * 공개 프로필 화면 모델 (SNS 2·3단계, sns-design §7.2·§7.3).
 *
 * <p>"남에게 보이는 공개 프로필"이라 <b>전부 PUBLIC 범위로 거른 값</b>만 담는다 — PRIVATE 책·세션은
 * 애초에 들어오지 않는다(필드 누락이 아니라 설계적 차단, §3.4). 이메일·타이머 내부값 등은 담지 않는다.
 *
 * <p>팔로우 관계는 <b>카운트만</b> 노출하고(목록 화면은 후속), 버튼 분기를 위해 viewer 기준
 * {@code following}/{@code self}를 함께 담는다.
 *
 * @param loginId        대상 사용자 공개 @핸들(login_id) — 프로필 URL·팔로우/차단/신고 폼의 식별자
 * @param nickname       대상 사용자 표시 이름(중복 가능) — 화면 제목·인사말에 보이는 이름
 * @param books          공개(PUBLIC) 책 목록(최신 등록 먼저)
 * @param bookTimes      책 id → 누적 독서 초(공개 책만)
 * @param graph          공개 잔디(PUBLIC 책 세션만 반영)
 * @param followerCount  팔로워 수(이 사용자를 팔로우하는 사람)
 * @param followingCount 팔로잉 수(이 사용자가 팔로우하는 사람)
 * @param following      viewer가 이 사용자를 팔로우 중인지(버튼 분기)
 * @param self           이 프로필이 viewer 본인인지(그러면 팔로우 버튼 없음)
 */
public record ProfileView(
        String loginId,
        String nickname,
        List<Book> books,
        Map<Long, Long> bookTimes,
        ContributionGraph graph,
        long followerCount,
        long followingCount,
        boolean following,
        boolean self) {
}
