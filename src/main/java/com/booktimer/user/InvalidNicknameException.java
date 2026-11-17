package com.booktimer.user;

/**
 * 닉네임이 이름 규칙(보이지 않는 글자 · 링크 · 금지어 — {@code ContentFilter} NAME)에 걸렸다(B2a REQ-265). 문구가 곧 사용자 안내다 —
 * 웹 설정 · 온보딩은 {@code nickname} 필드 오류로, 앱 블로그 문은 400 본문으로 그대로 보인다. 공백 · 길이 위반은 여기 아니다(기존 IAE).
 */
public class InvalidNicknameException extends IllegalArgumentException {

    public InvalidNicknameException(String message) {
        super(message);
    }
}
