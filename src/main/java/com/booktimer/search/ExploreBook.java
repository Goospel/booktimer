package com.booktimer.search;

/**
 * 둘러보기 카드에 세우는 책 한 권 — <b>표지와 제목뿐</b>이다.
 *
 * <p>누적 초·마지막으로 읽은 시각을 <b>의도적으로 담지 않는다</b>. 그 값들은 정렬의 재료로만 쓰이고
 * 화면에는 나가지 않는다 — 「언제 읽었는가」는 낯선 사람에게 보여줄 것이 아니라는 판단이다
 * (사용자 결정 2026-08-20). 담지 않으면 실수로 그릴 수도 없다.
 *
 * @param title    책 제목
 * @param coverUrl 표지 주소. 손으로 등록한 책은 {@code null}이라 미니앱이 이니셜 상자로 대신 그린다
 */
public record ExploreBook(String title, String coverUrl) {
}
