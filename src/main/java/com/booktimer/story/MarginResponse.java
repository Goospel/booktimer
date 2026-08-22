package com.booktimer.story;

import java.util.List;

/**
 * 책 하나의 여백 — 그 자리에 쌓인 글 목록 (2026-08-16 재설계).
 *
 * <p><b>자기완결</b>로 설계했다: 책 라벨·주인·관계를 함께 실어 화면이 다른 요청 없이 그려진다.
 * 진입로가 둘이기 때문 — 책방 격자에서 들어오면 클라가 이미 책을 알지만, 홈 소식에서 바로 점프하면
 * 아무것도 모르는 채 도착한다.
 *
 * <p>§3.4 화이트리스트: 노출 필드는 이 record 트리에 정의된 것뿐.
 *
 * <p><b>{@code following}은 2026-08-22에 뺐다</b> — 팔로우가 여백 노출에서 빠지면서 두 클라 어디도
 * 읽지 않게 됐고, 권한처럼 생긴 죽은 필드를 남기면 다음 사람이 게이트가 아직 있다고 읽는다.
 * 여백 화면에 팔로우 버튼을 세우는 날 되살리면 된다(한 줄이다).
 *
 * @param book          여백이 열린 책(라벨용)
 * @param ownerNickname 책 주인 표시 이름
 * @param self          viewer 본인의 책인가(작성·삭제 손잡이 분기)
 * @param entries       최신순 글 목록. 공개 책이면 누구에게나 실린다
 */
public record MarginResponse(MarginBook book, String ownerNickname, boolean self,
                             List<MarginEntry> entries) {
}
