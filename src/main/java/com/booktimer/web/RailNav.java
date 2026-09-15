package com.booktimer.web;

import com.booktimer.user.Role;

/**
 * 양옆 세로 바(side rails)의 경로 판정 — 순수 함수, Spring 무관.
 *
 * <p>설계: claude-docs/plans/2026-09-15-web-side-rails.md §4-①. 경로가 곧 모드다(홈만 저장값·Vue가 덮는다).
 */
public final class RailNav {

    public enum Key { HOME, BOOKS, SHOP, PERSONALITY, HISTORY, SEARCH, PLAN, RECALL, SBOOKS, SHISTORY }

    private RailNav() {
    }

    /** 경로 → 활성 키. 없으면 null. loginId는 내 책방 판정용(null 허용 — 남의 책방·온보딩 전은 활성 없음). */
    public static Key activeKey(String path, String loginId) {
        String p = stripTrailingSlash(path);
        switch (p) {
            case "/", "/dashboard": return Key.HOME;
            case "/personality": return Key.PERSONALITY;
            case "/history": return Key.HISTORY;
            case "/search": return Key.SEARCH;
            case "/study": return Key.PLAN;
            case "/study/recall": return Key.RECALL;
            case "/study/books": return Key.SBOOKS;
            case "/study/history": return Key.SHISTORY;
            default: break;
        }
        if (p.equals("/books") || p.startsWith("/books/")) return Key.BOOKS;
        if (loginId != null && !loginId.isBlank() && p.equals("/u/" + loginId)) return Key.SHOP;
        return null;
    }

    /** "/study" 정확 또는 "/study/" 접두면 "study", 아니면 "reading". */
    public static String mode(String path) {
        return path.equals("/study") || path.startsWith("/study/") ? "study" : "reading";
    }

    /** 내 책방 href — loginId blank 또는 ADMIN(책방이 404)이면 null(링크를 안 그린다). */
    public static String shopHref(String loginId, Role role) {
        if (loginId == null || loginId.isBlank() || role == Role.ADMIN) return null;
        return "/u/" + loginId;
    }

    private static String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
