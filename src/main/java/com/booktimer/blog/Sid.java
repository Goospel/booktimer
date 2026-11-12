package com.booktimer.blog;

import java.security.SecureRandom;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 문장 주소 sid — 대소문자를 가르는 base62 8자 · 전역 유일(계획 2026-11-11-b1-blog-core §5.1 · §2 #23 · #26).
 *
 * <p>한 번 올리기 안에서 새로 뽑은 sid끼리는 DB에 아직 없어 {@code existsBySid}가 못 본다 — 그래서 {@link #fresh}의
 * {@code taken}은 「이번에 뽑은 것 ∪ DB」다.
 */
public final class Sid {

    static final int LENGTH = 8;
    static final int MAX_TRIES = 10;
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Pattern VALID = Pattern.compile("[0-9A-Za-z]{8}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private Sid() {
    }

    public static String random() {
        char[] c = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) c[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        return new String(c);
    }

    /** 겹치지 않는 sid — {@value #MAX_TRIES}번 모두 겹치면 {@link IllegalStateException}(뽑기가 고장 났다는 뜻이다). */
    public static String fresh(Predicate<String> taken, Supplier<String> random) {
        for (int i = 0; i < MAX_TRIES; i++) {
            String sid = random.get();
            if (!taken.test(sid)) return sid;
        }
        throw new IllegalStateException("문장 주소를 " + MAX_TRIES + "번 뽑아도 모두 겹쳤어요");
    }

    /** 한 번 올리기 안의 sid — 「이번에 뽑은 것({@code drawn}) ∪ DB」를 피하고, 뽑은 것을 {@code drawn}에 더한다. */
    public static String fresh(Set<String> drawn, Predicate<String> inDb, Supplier<String> random) {
        String sid = fresh(s -> drawn.contains(s) || inDb.test(s), random);
        drawn.add(sid);
        return sid;
    }

    public static boolean valid(String sid) {
        return sid != null && VALID.matcher(sid).matches();
    }
}
