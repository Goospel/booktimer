package com.booktimer.search;

/**
 * 사용자 검색/목록 결과 한 줄 (sns-design §7.3, login-id-design §7 PR-3).
 *
 * <p>핸들과 표시 이름이 분리된다(인스타/X 모델): <b>식별·링크는 {@code loginId}(공개 @핸들)</b>로,
 * 화면에 보이는 이름은 {@code nickname}(중복 가능한 표시 이름)으로 그린다.
 *
 * @param loginId         공개 @핸들(login_id) — 프로필 링크·팔로우 폼의 대상 식별자(유니크·불변)
 * @param nickname        표시 이름(중복 가능) — 화면에 보이는 이름
 * @param publicBookCount 공개(PUBLIC)한 책 수 — "누구인지" 감 잡기용
 * @param following       검색한 사람(viewer)이 이미 이 사용자를 팔로우 중인지 — 버튼을 팔로우/언팔로 분기
 * @param self            이 사용자가 viewer 본인인지 — 그러면 팔로우 버튼 대신 "나" 표시
 */
public record UserSearchResult(
        String loginId,
        String nickname,
        long publicBookCount,
        boolean following,
        boolean self) {
}
