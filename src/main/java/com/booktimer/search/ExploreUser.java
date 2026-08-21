package com.booktimer.search;

import java.util.List;

/**
 * 둘러보기 카드 한 장 — 사람 한 줄 + 그 사람의 공개 책 최대 4권.
 *
 * <p>{@link RecommendedUser}와 형제지만 <b>이유 칩을 싣지 않는다</b>. 「같이 읽은 책 N권」은 칩 대신
 * 겹친 책을 앞자리에 세우는 것으로 말하고, 「공통 친구 N명」·「나를 팔로우함」은 책방(프로필)으로
 * 옮겼다(사용자 결정 2026-08-20). 그래서 이 카드에는 칩이 하나도 서지 않는다.
 *
 * @param row   공통 사용자 요약 행(핸들·표시이름·공개책수·팔로우여부·본인여부)
 * @param books 세울 책. <b>겹친 책이 앞</b>, 그 뒤는 누적 독서 시간 순. 최대 4권이고 4권 미만이면 있는 만큼
 */
public record ExploreUser(UserSearchResult row, List<ExploreBook> books) {
}
