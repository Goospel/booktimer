package com.booktimer.user;

/**
 * Apple 신원 연결 충돌(v1 REQ-03) — 그 sub가 이미 다른 계정에 있거나, 대상 계정에 이미 다른 Apple 신원이 있다.
 * 컨트롤러는 <b>409</b>로 매핑하고 메시지를 그대로 보인다 — 두 경우는 사용자가 할 일이 달라 문구를 가른다
 * (시각 보충 §8(다)). 코드가 틀린 400과는 구분된다.
 */
public class AppleLinkConflictException extends RuntimeException {

    static final String SUB_TAKEN = "이 Apple ID는 이미 다른 책생글 계정에 연결돼 있어요";
    static final String ACCOUNT_LINKED =
            "이 웹 계정에는 이미 다른 Apple ID가 연결돼 있어요 — 웹 설정에서 연결을 해제한 뒤 다시 해 주세요";

    AppleLinkConflictException(String message) {
        super(message);
    }
}
