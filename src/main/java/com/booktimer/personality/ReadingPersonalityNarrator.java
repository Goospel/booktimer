package com.booktimer.personality;

import java.util.Optional;

/**
 * 독서 성향 서술 생성기(포트) — 사실(독서 프로필)을 받아 자연어 성향 서술 + 태그를 만든다(책BTI Phase 3).
 *
 * <p>{@link com.booktimer.book.BookSearchClient}와 같은 정신의 포트 추상화다. 이 포트 덕분에
 * 서비스/테스트는 외부 LLM·키 없이 가짜 구현으로 검증되고, 공급자(Gemini→다른 LLM)를 갈아도 호출부가 안 흔들린다.
 */
public interface ReadingPersonalityNarrator {

    /** 서술 생성 활성 여부 — LLM API 키가 없으면 false(화면은 사실만 표시로 폴백). */
    boolean isEnabled();

    /**
     * 독서 프로필(사실)을 해석해 성향 서술 + 태그를 생성한다.
     *
     * <p>비활성(키 없음)·입력 없음·외부 호출 실패/지연이면 빈 {@link Optional} — 호출자는 사실만 표시로 폴백한다.
     * 외부 LLM 장애가 화면을 깨지 않게 격리하는 것이 핵심(알라딘 검색 폴백과 동일).
     *
     * @param profile 코드가 집계한 사실
     */
    Optional<PersonalityNarration> narrate(ReadingProfile profile);
}
