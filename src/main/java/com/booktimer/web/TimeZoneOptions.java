package com.booktimer.web;

import java.time.ZoneId;
import java.util.List;

/**
 * 타임존 드롭다운에 채울 IANA zone ID 목록 제공자.
 *
 * <p>사용자가 타임존 문자열 형식("Asia/Seoul")을 모르고도 고를 수 있도록, 자유 입력 대신 select로
 * 보여줄 후보를 만든다. {@link ZoneId#getAvailableZoneIds()}에서 <b>지역/도시 canonical 이름</b>만
 * 추린다 — 짧은 레거시 별칭(EST, GMT)이나 사람이 알아보기 힘든 가상 존(Etc/GMT+9는 부호가 뒤집혀
 * 혼란, SystemV/*)은 뺀다. 결과는 정렬되어 불변 목록으로 한 번만 계산해 캐시한다.
 */
public final class TimeZoneOptions {

    /** 애플리케이션 수명 동안 불변이므로 한 번만 계산해 재사용한다. */
    private static final List<String> ZONES = ZoneId.getAvailableZoneIds().stream()
            .filter(id -> id.contains("/"))           // 지역/도시 canonical만 (EST·GMT·UTC 같은 짧은 별칭 제외)
            .filter(id -> !id.startsWith("Etc/"))      // Etc/GMT±N: 부호 반전으로 혼란
            .filter(id -> !id.startsWith("SystemV/"))  // 레거시 SystemV 별칭
            .sorted()
            .toList();

    private TimeZoneOptions() {
        // 유틸리티 클래스
    }

    /** 정렬된 타임존 후보 목록(불변). */
    public static List<String> all() {
        return ZONES;
    }
}
