package com.booktimer.chat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 연락처·외부 링크·금전 유도 표시기 — <b>막지 않고 표시만</b> 한다(설계 §4-2 4단).
 *
 * <p>단독으로 잡는 실패: 표시해야 할 패턴을 놓치는 것(양성), 그리고 책 이야기 같은 평범한 문장까지
 * 표시해 수신자 배너가 늘 뜨는 것(음성 — 이 쪽이 없으면 「전부 true」 구현도 초록이다).
 */
class ContactExchangeDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678로 연락 주세요",
            "01012345678",
            "010 1234 5678",
            "https://example.com 여기 봐요",
            "www.naver.com",
            "open.kakao.com/o/abcd",
            "카톡 아이디 알려드릴게요",
            "카카오톡으로 얘기해요",
            "텔레그램 하세요?",
            "t.me/someone",
            "조건 맞으면 만나요",
            "만남 가능하세요",
            "수익 보장해 드립니다",
            "먼저 입금해 주시면 돼요"
    })
    void flagsContactLinksAndMoneyBait(String text) {
        assertThat(ContactExchangeDetector.suspicious(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "이 책 정말 좋았어요",
            "3장까지 읽었어요, 2026년 목표는 50권!",
            "ISBN 9788936434120 이 판이 번역이 좋아요",
            "오늘 120쪽 읽음",
            "작가가 말하는 사랑의 형태가 인상적이었어"
    })
    void leavesOrdinaryBookTalkAlone(String text) {
        assertThat(ContactExchangeDetector.suspicious(text)).isFalse();
    }
}
