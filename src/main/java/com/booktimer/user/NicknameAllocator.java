package com.booktimer.user;

import java.util.function.Predicate;

/**
 * 충돌하지 않는 닉네임을 고르는 순수 헬퍼.
 *
 * <p>닉네임은 유니크해야 하지만, 소셜 로그인은 provider가 준 표시 이름을 거부할 수 없다(로그인 자체를
 * 막을 순 없다). 그래서 충돌하면 거부 대신 {@code base-2}, {@code base-3} … 로 비어 있는 첫 후보를
 * 자동 할당한다. "이미 쓰이는가"는 호출자가 {@link Predicate}로 주입한다(DB 조회 등) — 이 클래스는
 * 스프링·DB를 모르는 순수 로직이라 격리 단위 테스트가 쉽다(N-009).
 */
public final class NicknameAllocator {

    private NicknameAllocator() {
        // 유틸리티 — 인스턴스화 금지
    }

    /**
     * {@code base}가 비어 있으면 그대로, 아니면 {@code base-2}, {@code base-3} … 중 처음으로 비어 있는 후보를 반환한다.
     *
     * @param base  희망 닉네임
     * @param taken 후보가 이미 쓰이는지 판단(예: {@code userRepository::existsByNickname})
     * @return 충돌하지 않는 닉네임
     */
    public static String firstAvailable(String base, Predicate<String> taken) {
        if (!taken.test(base)) {
            return base;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = base + "-" + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
    }
}
