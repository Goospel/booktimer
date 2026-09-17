package com.booktimer.web;

import com.booktimer.user.User;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 세로 바 뷰 모델 — Thymeleaf가 getter로 읽는다.
 *
 * <p>사용자는 <b>첫 getter에서 한 번만</b> 해석한다. {@code @ModelAttribute}는 REST·바 없는 화면에서도
 * 실행되므로, 즉시 해석하면 모든 요청에 DB 조회가 붙는다(설계 §3-2).
 */
public final class RailModel {

    private final String path;
    private final Supplier<User> userSupplier;
    private User user;

    public RailModel(String path, Supplier<User> userSupplier) {
        this.path = path;
        this.userSupplier = userSupplier;
    }

    private User user() {
        if (user == null) user = userSupplier.get();
        return user;
    }

    /** 바 모드("reading"|"study") — 홈·중립 화면은 null이라 속성이 안 그려지고 인라인 부트가 저장값으로 채운다. */
    public String getMode() {
        return RailNav.pageMode(path, user().getLoginId());
    }

    /** 활성 메뉴 키(enum 이름 소문자) — 없으면 null. */
    public String getActiveKey() {
        RailNav.Key key = RailNav.activeKey(path, user().getLoginId());
        return key == null ? null : key.name().toLowerCase(Locale.ROOT);
    }

    /** 홈이 열릴 모드("reading"|"study") — 기억하지 않을 화면(홈·중립)이면 null이라 속성이 안 그려진다. */
    public String getRememberMode() {
        return RailNav.rememberMode(path, user().getLoginId());
    }

    public String getShopHref() {
        return RailNav.shopHref(user().getLoginId(), user().getRole());
    }
}
