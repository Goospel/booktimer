package com.booktimer.user;

/**
 * 웹(또는 미니앱)에서 만든 계정을 아이폰 앱에서 지우려 했다(v1 REQ-07) — 앱은 409로 막고 웹 설정 삭제(/settings#delete)로 보낸다.
 * 연결 코드가 새 공격자 Apple ID가 붙어도 앱 재인증만으로는 데이터를 지우지 못하게 하는 장치다. 이때 아무것도 지우지 않는다.
 */
public class WebAccountDeletionException extends RuntimeException {

    public WebAccountDeletionException() {
        super("web account must be deleted on the web");
    }
}
