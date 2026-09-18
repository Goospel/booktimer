package com.booktimer.chat;

import org.springframework.http.HttpStatus;

/**
 * 대화 경로의 거부 — 상태코드와 <b>사용자에게 그대로 띄울 한국어 문구</b>를 함께 든다
 * (미니앱 {@code api.ts errorMessage()}는 에러 본문 평문을 안내로 쓴다).
 */
public class ChatException extends RuntimeException {

    private final HttpStatus status;

    private ChatException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 방이 없거나 내가 멤버가 아니다 — 둘을 구분하지 않는다(남의 방 존재 비누설). */
    static ChatException notFound() {
        return new ChatException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없어요.");
    }

    static ChatException denied(ChatEligibility.Verdict verdict) {
        return switch (verdict) {
            case UNREACHABLE -> new ChatException(HttpStatus.CONFLICT, "상대는 아직 앱에서 대화를 쓸 수 없어요.");
            case BLOCKED -> new ChatException(HttpStatus.FORBIDDEN, "대화할 수 없는 상대예요.");
            case RESTRICTED -> new ChatException(HttpStatus.FORBIDDEN, "지금은 대화를 보낼 수 없어요.");
            default -> new ChatException(HttpStatus.FORBIDDEN, "서로 팔로우해야 메시지를 보낼 수 있어요.");
        };
    }

    static ChatException closed() {
        return new ChatException(HttpStatus.CONFLICT, "종료된 대화예요.");
    }

    static ChatException rateLimited() {
        return new ChatException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 보내 주세요.");
    }

    static ChatException badRequest(String message) {
        return new ChatException(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
