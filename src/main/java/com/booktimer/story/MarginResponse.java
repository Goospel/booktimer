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
 * @param book          여백이 열린 책(라벨용). 비팔로워에게도 실린다 — 격자에 이미 보이는 공개 책이다
 * @param ownerNickname 책 주인 표시 이름
 * @param self          viewer 본인의 책인가(작성·삭제 손잡이 분기)
 * @param following     viewer가 주인을 팔로우 중인가. 본인이면 항상 false(자기 자신은 팔로우 대상이 아니다)
 * @param entries       최신순 글 목록. <b>비팔로워면 빈 배열</b> — 글 유무 정보도 새지 않게(§13.2)
 */
public record MarginResponse(MarginBook book, String ownerNickname, boolean self, boolean following,
                             List<MarginEntry> entries) {
}
