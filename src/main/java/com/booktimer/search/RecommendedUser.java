package com.booktimer.search;

import java.util.List;

/**
 * 친구 추천 한 줄 — 사용자 요약 행({@link UserSearchResult}) + "추천 이유" 칩 — 친구 추천 하이브리드 1단계.
 *
 * <p>추천에만 이유를 싣기 위해 공유 타입 {@link UserSearchResult}(검색·팔로워/팔로잉·차단 목록 등 6곳이
 * 공유)를 <b>건드리지 않고</b> 감싼다 — 검색 결과 행에 빈 reason이 새지 않게 분리한 것.
 *
 * @param row     공통 사용자 요약 행(핸들·표시이름·공개책수·팔로우여부·본인여부)
 * @param reasons 추천 이유 칩(예: "나를 팔로우함", "공통 친구 3명", "같이 읽은 책 2권"). 폴백 후보는 빈 목록.
 */
public record RecommendedUser(UserSearchResult row, List<String> reasons) {
}
