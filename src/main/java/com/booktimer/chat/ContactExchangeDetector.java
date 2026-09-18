package com.booktimer.chat;

import java.util.regex.Pattern;

/**
 * 연락처·외부 링크·메신저 ID·금전 유도 <b>표시기</b>(설계 §4-2 4단). 막지 않는다 — {@code flagged}로 표시만 하고
 * 수신자 안내 배너·관리자 대본 강조에 쓴다(맞팔끼리의 대화에서 차단 필터는 오탐 비용이 더 크다).
 *
 * <p>ponytail: 정규식 한 벌이다. 「공일공」 같은 우회 표기는 못 잡는다 — 신고가 쌓이면 그때 넓힌다.
 */
public final class ContactExchangeDetector {

    private static final Pattern SUSPICIOUS = Pattern.compile(String.join("|",
            "01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}",            // 휴대폰 번호
            "https?://", "www\\.", "open\\.kakao", "t\\.me/",       // 링크·오픈채팅·텔레그램 링크
            "카톡", "카카오톡", "오픈채팅", "텔레그램", "텔레\\s?아이디", "라인\\s?아이디",
            "조건", "만남", "수익\\s?보장", "입금"),
            Pattern.CASE_INSENSITIVE);

    private ContactExchangeDetector() {
    }

    public static boolean suspicious(String text) {
        return text != null && SUSPICIOUS.matcher(text).find();
    }
}
