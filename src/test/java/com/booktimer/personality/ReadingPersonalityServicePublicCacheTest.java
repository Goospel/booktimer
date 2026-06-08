package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 책방 노출용 공개 책BTI 캐시 통합 테스트(Phase 6) — {@code analyzePublicCached}의 캐시/재생성/콜드스타트/폴백.
 *
 * <p>본인용 캐시({@link ReadingPersonalityServiceCacheTest})와 같은 불변식을 따르되, 입력이 <b>공개(PUBLIC)+완독
 * 책만</b>이고 저장소가 <b>별도 테이블</b>(reading_personality_public)이라는 점이 다르다. 핵심:
 * (1) 첫 분석 시 LLM 호출 + 공개 캐시 저장, (2) 공개 책장 안 변하면 캐시 사용, (3) force/시그니처 변동이면 재생성,
 * (4) 공개+완독 책 부족이면 콜드스타트(LLM·캐시 없음), (5) <b>본인용 캐시와 완전히 분리</b>(서로 안 건드림).
 */
@SpringBootTest
@Transactional
class ReadingPersonalityServicePublicCacheTest {

    @Autowired
    private ReadingPersonalityService service;
    @Autowired
    private PublicReadingPersonalityCacheRepository publicCacheRepository;
    @Autowired
    private ReadingPersonalityCacheRepository ownerCacheRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;

    @MockitoBean
    private ReadingPersonalityNarrator narrator;

    private User newUser(String email) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    /** 공개(PUBLIC)+완독 책 n권을 적재한다 — 공개 콜드스타트 임계(5)용. */
    private void savePublicBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            Book b = Book.register(u, "공개책" + i, "저자" + i, null, null, null, null,
                    null, null, BookStatus.FINISHED);
            b.makePublic();
            bookRepository.save(b);
        }
    }

    private void narratorReturns(String narrative) {
        when(narrator.narrate(any())).thenReturn(Optional.of(new PersonalityNarration(narrative, List.of("태그"))));
    }

    @Test
    @DisplayName("첫 공개 분석: LLM을 호출하고 공개 캐시 테이블에 저장한다")
    void firstPublicAnalysis_callsLlmAndCachesInPublicTable() {
        User u = newUser("pubfirst@booktimer.com");
        savePublicBooks(u, 5);
        narratorReturns("공개 책 기준 완독러.");

        ReadingPersonality result = service.analyzePublicCached(u, false);

        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("공개 책 기준 완독러.");
        verify(narrator, times(1)).narrate(any());
        assertThat(publicCacheRepository.findByUser(u)).isPresent();
        // 본인용 캐시는 건드리지 않는다(완전 분리)
        assertThat(ownerCacheRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("공개 캐시 히트: 공개 책장이 그대로면 LLM을 다시 부르지 않는다")
    void publicCacheHit_doesNotCallLlmAgain() {
        User u = newUser("pubhit@booktimer.com");
        savePublicBooks(u, 5);
        narratorReturns("정독형.");

        service.analyzePublicCached(u, false);
        service.analyzePublicCached(u, false);

        verify(narrator, times(1)).narrate(any());
    }

    @Test
    @DisplayName("force: 공개 책장이 같아도 강제 재생성한다")
    void publicForce_regenerates() {
        User u = newUser("pubforce@booktimer.com");
        savePublicBooks(u, 5);
        narratorReturns("서술.");

        service.analyzePublicCached(u, false);
        service.analyzePublicCached(u, true);

        verify(narrator, times(2)).narrate(any());
    }

    @Test
    @DisplayName("공개 콜드스타트: 공개+완독 책이 임계 미만이면 LLM·캐시 없이 사실만 보류한다")
    void publicColdStart_noLlmNoCache() {
        User u = newUser("pubcold@booktimer.com");
        savePublicBooks(u, 3);                 // 공개 완독 3권(임계 5 미만)
        // 비공개 완독 책을 잔뜩 둬도 공개 콜드스타트엔 영향 없어야 한다
        for (int i = 0; i < 5; i++) {
            bookRepository.save(Book.register(u, "비공개" + i, "저자", null, null, null, null,
                    null, null, BookStatus.FINISHED));
        }

        ReadingPersonality result = service.analyzePublicCached(u, false);

        assertThat(result.hasNarration()).isFalse();
        verify(narrator, never()).narrate(any());
        assertThat(publicCacheRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("공개 LLM 실패(직전 캐시 없음): 사실만 폴백하고 공개 캐시는 안 만든다")
    void publicLlmFails_noPriorCache_fallback() {
        User u = newUser("pubfail@booktimer.com");
        savePublicBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(Optional.empty());

        ReadingPersonality result = service.analyzePublicCached(u, false);

        assertThat(result.hasNarration()).isFalse();
        assertThat(publicCacheRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("본인용/공개 캐시 분리: 본인 analyzeCached가 공개 캐시를 만들지 않는다")
    void ownerAnalysis_doesNotPopulatePublicCache() {
        User u = newUser("sep@booktimer.com");
        savePublicBooks(u, 5); // 공개+완독이라 본인용에도 잡힘
        narratorReturns("서술.");

        service.analyzeCached(u, false); // 본인용 경로

        assertThat(ownerCacheRepository.findByUser(u)).isPresent();
        assertThat(publicCacheRepository.findByUser(u)).isEmpty(); // 공개 캐시는 비어 있어야
    }
}
