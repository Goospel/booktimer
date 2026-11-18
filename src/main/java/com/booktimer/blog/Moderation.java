package com.booktimer.blog;

import java.time.Duration;
import java.time.Instant;

/**
 * 운영자 가림 상태(B2b · V109 · 계획 2026-11-16-blog-b2 §4.5 · §6.5) — 글 · 댓글 · 인용 박스가 같은 열 셋을 쓴다:
 * {@code hidden_at}(null = 보임) · {@code hide_kind} · {@code hide_note}(게시자에게만 보이는 운영자 글).
 * 상태의 단일 출처는 그 행의 열이다(처리 기록 {@link BlogModerationLog}는 쓰기만 하는 감사 줄).
 */
public final class Moderation {

    /** TEMP 임시조치 30일(약관 ⑥) · COPYRIGHT 저작권 중단 요구(⑦ — 만료 없음) · POLICY 정책 위반(⑧) · REMOVED 운영자 삭제. */
    public enum Kind { TEMP, COPYRIGHT, POLICY, REMOVED }

    /** 운영자 행동 — {@code REMOVE}는 내용을 지우고 자리는 남긴다(약관 179줄). */
    public enum Action { TEMP, COPYRIGHT, POLICY, RESTORE, REMOVE }

    static final int TEMP_DAYS = 30;
    public static final int NOTE_MAX = 2_000;
    private static final Duration TEMP = Duration.ofDays(TEMP_DAYS);

    /** 게시자에게 가는 상태 — {@code until}은 TEMP만, {@code note}는 게시자 DTO에만 싣는다(REQ-334). */
    public record View(String kind, Instant until, String note) {
    }

    /** 전이 결과 — {@code changed}가 거짓이면 처리 기록 · 메일 0(REMOVED → REMOVE는 내용만 다시 비운다). */
    record Next(Instant hiddenAt, Kind kind, String note, boolean changed) {
    }

    private Moderation() {
    }

    /** 안 가렸으면 null · 가렸는데 kind가 null이면 POLICY(B1 시험의 JDBC 가림 픽스처 꼴 — 운영엔 없다). */
    static Kind kindOf(Instant hiddenAt, Kind kind) {
        if (hiddenAt == null) return null;
        return kind == null ? Kind.POLICY : kind;
    }

    static View of(Instant hiddenAt, Kind kind, String note) {
        Kind k = kindOf(hiddenAt, kind);
        if (k == null) return null;
        return new View(k.name(), k == Kind.TEMP ? hiddenAt.plus(TEMP) : null, note);
    }

    static boolean expired(Instant hiddenAt, Kind kind, Instant now) {
        return kindOf(hiddenAt, kind) == Kind.TEMP && !hiddenAt.plus(TEMP).isAfter(now);
    }

    /**
     * §6.5 전이 표. TEMP로 갈 때만 {@code hidden_at}을 지금으로(30일을 새로 센다) · 다른 가림으로 갈 때는 처음 가린 시각 그대로.
     * note는 비우지 않으면 덮어쓰고 RESTORE는 지운다.
     *
     * @throws IllegalArgumentException REMOVED에서 가림 · 다시 보이기(내용이 없다 — 400)
     */
    static Next next(Instant hiddenAt, Kind kind, String oldNote, Action action, String note, Instant now) {
        Kind cur = kindOf(hiddenAt, kind);
        if (cur == Kind.REMOVED) {
            if (action != Action.REMOVE) {
                throw new IllegalArgumentException(action == Action.RESTORE ? "내용을 지운 게시물은 다시 보이게 할 수 없어요"
                        : "내용을 지운 게시물은 다시 가릴 수 없어요");
            }
            return new Next(hiddenAt, Kind.REMOVED, oldNote, false);
        }
        if (action == Action.RESTORE) {
            return cur == null ? new Next(null, null, oldNote, false) : new Next(null, null, null, true);
        }
        Kind to = action == Action.REMOVE ? Kind.REMOVED : Kind.valueOf(action.name());
        if (to == cur) {
            return new Next(hiddenAt, kind, oldNote, false);
        }
        String n = note == null || note.isBlank() ? oldNote : note.strip();
        if (n != null && n.length() > NOTE_MAX) {
            throw new IllegalArgumentException("게시자에게 보일 글은 2,000자까지예요");
        }
        return new Next(cur == null || to == Kind.TEMP ? now : hiddenAt, to, n, true);
    }
}
