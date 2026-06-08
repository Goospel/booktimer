package com.booktimer.personality;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 독서 프로필의 "입력 시그니처" — 책장이 <b>의미있게</b> 바뀌었는지 감지하는 해시(책BTI Phase 4).
 *
 * <p>캐시된 성향 서술이 아직 유효한지 판단하는 키다. 같은 시그니처면 LLM을 다시 부르지 않고 캐시를 쓴다
 * (비용=호출 빈도를 바닥으로). 시그니처가 달라지면 책장이 의미있게 변한 것 → 재생성.
 *
 * <p>무엇을 "의미있는 변화"로 볼지가 핵심이다 — 완독 책의 권수·저자/장르/연대 분포 같은 <b>구조</b>만 넣는다.
 * <b>독서 시간은 넣지 않는다</b>: 성향은 완독 책 내용에서만 뽑으므로(시간은 입력이 아님), 시간을 넣으면 책을
 * 더 완독하지 않고 시간만 쌓여도 재생성돼 같은 결과를 다시 만드는 낭비가 된다.
 */
public final class ProfileSignature {

    private ProfileSignature() {
    }

    /** 프로필의 결정적 시그니처(SHA-256 hex). 같은 프로필이면 항상 같은 값. */
    public static String of(ReadingProfile profile) {
        return sha256Hex(canonical(profile));
    }

    /** 시그니처에 들어가는 정규 문자열 — 무엇을 "의미있는 변화"로 보는지의 단일 정의. */
    static String canonical(ReadingProfile p) {
        return new StringBuilder()
                .append("books=").append(p.totalBooks())
                .append(";fin=").append(p.finishedBooks())
                .append(";read=").append(p.readingBooks())
                .append(";want=").append(p.wantToReadBooks())
                .append(";distAuthors=").append(p.distinctAuthors())
                .append(";authors=").append(labels(p.topAuthors()))
                .append(";distGenres=").append(p.distinctGenres())
                .append(";genres=").append(labels(p.topGenres()))
                .append(";decades=").append(labels(p.pubDecades()))
                .toString();
    }

    private static String labels(List<LabeledCount> list) {
        return list.stream().map(c -> c.label() + ":" + c.count()).collect(Collectors.joining(","));
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
