package com.booktimer.book;

import java.util.regex.Pattern;

/**
 * 여러 권을 묶어 파는 <b>세트 상품</b>인가 — 제목 문자열 휴리스틱.
 *
 * <p>「이어 읽기」 추천도 책 검색도 <b>다음에 읽을 한 권</b>을 고르는 자리다. 33권 묶음은 읽을 단위가
 * 아니라 구매 단위라 그 목록에 섞이면 잡음이 된다. 실기기 제보(2026-08-21)로 드러난 「노벨라33 세트 -
 * 전33권」이 그 예다 — 저자 40명이 목록 한 줄을 세로 900px로 부풀려 카드를 통째로 먹었다.
 *
 * <p>⚠️ <b>화면이 깨진 것과 세트를 거르는 것은 다른 문제다.</b> 줄 높이는 화면이 따로 고정하므로
 * (제목·저자 각 한 줄) 이 판정이 실패해도 화면은 안 깨진다. 여기가 다루는 것은 <b>추천·검색 품질</b>뿐이다.
 *
 * <p>⚠️ <b>오탐이 진짜 비용이다.</b> 걸러진 책은 검색에서 사라져 <b>가진 책을 담을 길이 막힌다</b>.
 * 그래서 ① 판정을 좁게 잡고(「전집」·「전권」처럼 그 자체로 한 권일 수 있는 표기는 통과시킨다)
 * ② {@link #wanted(String)}로 <b>일부러 세트를 찾는 검색어에는 아예 적용하지 않는다</b>.
 *
 * <p>판정을 이 한 클래스에 모아 둔 이유는 튜닝 지점을 하나로 두기 위해서다 — 오탐이 보고되면
 * 여기만 고치면 검색·추천이 함께 따라온다.
 */
public final class BookSetTitle {

    /**
     * 「전N권」 — 묶음의 가장 확실한 표식이다. 한 권짜리 책이 자기 제목에 권수를 이렇게 쓰는 일은 없다.
     *
     * <p>숫자를 <b>요구</b>하는 것이 핵심이다: 「전집」·「전권」은 그 자체로 한 권인 책이 있어(『셜록 홈즈
     * 전집』) 걸러선 안 된다. 공백은 섞여도 통과시킨다(「전 21 권」).
     */
    private static final Pattern VOLUME_COUNT = Pattern.compile("전\\s*\\d+\\s*권");

    /** 「세트」·「박스세트」 — 「전N권」이 안 붙은 묶음을 잡는다. */
    private static final String SET_WORD = "세트";

    private BookSetTitle() {
    }

    /**
     * 이 제목이 세트 상품인가.
     *
     * @param title 책 제목(null 허용)
     * @return 「전N권」 또는 「세트」가 있으면 true. null·공백은 false(판정 불가를 「세트」로 읽지 않는다)
     */
    public static boolean isSet(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        return title.contains(SET_WORD) || VOLUME_COUNT.matcher(title).find();
    }

    /**
     * 검색어가 <b>세트를 찾고 있는가</b> — 그렇다면 결과에서 세트를 빼지 않는다.
     *
     * <p>판정식은 {@link #isSet}과 같지만 <b>묻는 것이 다르다</b>: 저쪽은 「이 책이 묶음인가」이고
     * 이쪽은 「이 사람이 묶음을 찾는가」다. 이 갈래가 없으면 「노벨라33 세트」를 가진 사람이 그 책을
     * 서재에 담을 길이 아예 없어진다 — 걸러 내기의 유일한 탈출구다.
     *
     * @param query 사용자가 입력한 검색어(null 허용)
     */
    public static boolean wanted(String query) {
        return isSet(query);
    }
}
