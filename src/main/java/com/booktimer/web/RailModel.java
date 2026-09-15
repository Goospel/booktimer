package com.booktimer.web;

import com.booktimer.user.User;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 양옆 세로 바 뷰 모델 — Thymeleaf가 getter로 읽는다.
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

    public String getMode() {
        return RailNav.mode(path);
    }

    /** 활성 메뉴 키(enum 이름 소문자) — 없으면 null. */
    public String getActiveKey() {
        RailNav.Key key = RailNav.activeKey(path, user().getLoginId());
        return key == null ? null : key.name().toLowerCase(Locale.ROOT);
    }

    public String getShopHref() {
        return RailNav.shopHref(user().getLoginId(), user().getRole());
    }
}
