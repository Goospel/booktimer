package com.booktimer.writing;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.story.StoryService;
import com.booktimer.study.StudyAiDailyTotal;
import com.booktimer.study.StudyAiDailyTotalRepository;
import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.study.StudyAiUsageService;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.AccountService;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import com.booktimer.writing.ClaudeWritingAssistant.Failure;
import com.booktimer.writing.ClaudeWritingAssistant.Questions;
import com.booktimer.writing.ClaudeWritingAssistant.Usage;
import com.booktimer.writing.ClaudeWritingAssistant.WritingAiResult;
import com.booktimer.writing.WritingAiService.AiView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * AI 단계 유스케이스(v1 REQ-18~22 · 27 · 46 · 47) — 가짜 어댑터 + 실 H2.
 *
 * <p>순서 규율(REQ-19)을 행동으로 잰다: 막히는 갈래마다 「어댑터 호출 0 · 몫 그대로 · 비용 0」을, 실패 갈래마다 「두 몫 환불 · 비용은
 * 규칙대로」를 단언한다. 양성 대조(동의 뒤 200 · 7라운드는 통과 · 30자는 통과)가 같이 있어 「늘 거절하는」 구현은 떨어진다.
 */
@MockedBoundaryTest
@Transactional
class WritingAiServiceTest {

    private static final Instant CONSENT_AT = Instant.parse("2026-11-23T00:00:00Z");
    private static final String MODEL = "claude-haiku-5-5";

    @Autowired WritingAiService aiService;
    @Autowired WritingService writingService;
    @Autowired ClaudeWritingAssistant assistant;
    @Autowired AppleUserProvisioningService appleUsers;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired StoryService storyService;
    @Autowired StudyAiUsageService usageService;
    @Autowired StudyAiDailyTotalRepository dailyTotalRepository;
    @Autowired AiSpendMonthRepository spendRepository;
    @Autowired AiBudgetService budgetService;
    @Autowired AccountService accountService;
    @Autowired Clock clock;

    private static final AtomicInteger SEQ = new AtomicInteger();

    @BeforeEach
    void enabled() {
        given(assistant.isEnabled()).willReturn(true);
        given(assistant.model()).willReturn(MODEL);
    }

    // ── 준비 ─────────────────────────────────────────────────────────────────

    private User user(boolean consent) {
        User u = appleUsers.register("000777.writing.ai." + SEQ.incrementAndGet());
        if (consent) {
            u.consentToAi(CONSENT_AT);
            u = userRepository.saveAndFlush(u);
        }
        return u;
    }

    /**
     * 쪽지 한 장 — {@code withMaterial}이면 같은 책에 다른 쪽지(옮긴 문장 + 생각)가 하나 더 있다(배경 재료).
     * {@code text}가 null이면 옮긴 문장만 남긴 쪽지다. 재료가 없는 쪽지({@code withMaterial} = false · {@code text} = null)는
     * 옮긴 글에서만 생긴다 — AI 기록만 남고 생각 · 옮긴 문장이 빈 쪽지(J4).
     */
    private Story note(User u, boolean withMaterial, String text) {
        Book book = bookRepository.saveAndFlush(Book.register(u, "데미안", "헤르만 헤세", null, null, null, null, BookStatus.READING));
        if (withMaterial) {
            storyRepository.saveAndFlush(Story.of(u, "나도 껍질을 깨고 싶었다", book, "paper", "새는 알에서 나오려고 투쟁한다"));
        }
        if (text != null) {
            return storyRepository.saveAndFlush(Story.of(u, text, book, null));
        }
        if (withMaterial) {
            return storyRepository.saveAndFlush(Story.of(u, "", book, null, "알은 세계다"));
        }
        Story moved = Story.of(u, "옮겨 온 글", book, null);
        moved.recordRound("{\"rounds\":[]}", 0, CONSENT_AT);
        moved.edit(null, "", 0);
        return storyRepository.saveAndFlush(moved);
    }

    private static WritingAiResult<Questions> answer(String... texts) {
        return new WritingAiResult<>(new Questions(java.util.Arrays.stream(texts).map(t -> new Question(t, null)).toList()),
                null, new Usage(1_000, 0, 0, 500));
    }

    private static WritingAiResult<Questions> fail(Failure failure, Usage usage) {
        return new WritingAiResult<>(null, failure, usage);
    }

    private HttpStatus statusOf(Runnable call) {
        ResponseStatusException e = catchThrowableOfType(ResponseStatusException.class, call::run);
        assertThat(e).as("ResponseStatusException이 나야 한다").isNotNull();
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    private String reasonOf(Runnable call) {
        return catchThrowableOfType(ResponseStatusException.class, call::run).getReason();
    }

    private int remaining(User u) {
        return usageService.remaining(u, clock.instant(), Kind.WRITE);
    }

    private int globalUsed() {
        return dailyTotalRepository.findByUsageDate(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
                .map(StudyAiDailyTotal::getUsed).orElse(0);
    }

    private long monthSpend() {
        return spendRepository.findBySpendMonth(YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).toString())
                .map(AiSpendMonth::getMicroUsd).orElse(0L);
    }

    /** 이 글에 라운드 n개를 미리 쌓는다(질문 텍스트 = "r{i}-q"). */
    private void rounds(Story d, int n) {
        QuestionLog log = QuestionLog.parse(d.getAiLog());
        for (int i = 1; i <= n; i++) {
            log = log.append(new QuestionLog.Round(Step.ASK, CONSENT_AT, null, 0,
                    List.of(new Question("r" + i + "-a?", null), new Question("r" + i + "-b?", null))));
        }
        d.recordRound(log.toJson(), 0, CONSENT_AT);
        storyRepository.saveAndFlush(d);
    }

    // ── REQ-18 동의 게이트 ────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-18 · 동의 없는 AI 단계는 403이고 몫·호출·비용이 0")
    void noConsentIs403() {
        User u = user(false);
        Story d = note(u, true, null);
        int globalBefore = globalUsed();

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(globalUsed()).isEqualTo(globalBefore);
        verifyNoInteractions(assistant);
        assertThat(storyRepository.findById(d.getId()).orElseThrow().getAiCostMicroUsd()).isZero();
    }

    @Test
    @DisplayName("REQ-18 · 동의 뒤 같은 요청은 200이고 어댑터 호출이 1")
    void consentedCallsOnce() {
        User u = user(true);
        Story d = note(u, true, null);
        given(assistant.ask(anyString())).willReturn(answer("어떤 투쟁이었나요?"));

        AiView view = aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null);

        verify(assistant).ask(anyString());
        assertThat(view.questions().rounds()).hasSize(1);
        assertThat(view.aiRemainingToday()).isEqualTo(Kind.WRITE.max() - 1);
    }

    // ── REQ-19 순서 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-19 · 키가 없으면 몫을 깎기 전에 503")
    void noKeyIs503BeforeQuota() {
        User u = user(true);
        Story d = note(u, true, null);
        given(assistant.isEnabled()).willReturn(false);

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(reasonOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null))).contains("꺼져 있어요");
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        verify(assistant, never()).ask(anyString());
    }

    @Test
    @DisplayName("REQ-19 · revision이 다르면 409이고 몫·호출·비용이 0")
    void staleRevisionIs409BeforeQuota() {
        User u = user(true);
        Story d = note(u, true, null);
        int globalBefore = globalUsed();
        given(assistant.ask(anyString())).willReturn(answer("어떤 투쟁이었나요?"));

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision() + 1, null)))
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(globalUsed()).isEqualTo(globalBefore);
        verify(assistant, never()).ask(anyString());
        assertThat(storyRepository.findById(d.getId()).orElseThrow().getAiCostMicroUsd()).isZero();
        // 양성 대조 — 맞는 revision이면 라운드가 생긴다
        assertThat(aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null).questions().rounds()).hasSize(1);
    }

    @Test
    @DisplayName("REQ-19 · 어댑터 실패는 두 몫을 환불한다")
    void failureRefundsBoth() {
        User u = user(true);
        Story d = note(u, true, null);
        int globalBefore = globalUsed();
        given(assistant.ask(anyString())).willReturn(fail(Failure.UNAVAILABLE, null));

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(globalUsed()).isEqualTo(globalBefore);
        // 양성 대조 — 성공은 두 몫을 쓴다(위 단언이 「선점이 아예 없는」 구현으로 통과하지 않게)
        given(assistant.ask(anyString())).willReturn(answer("왜 그랬나요?"));
        aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max() - 1);
        assertThat(globalUsed()).isEqualTo(globalBefore + 1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 운영처럼 트랜잭션 없이 — 결과 저장이 낡은 사본을 덮는지 재려면 실제 커밋이 필요하다
    @DisplayName("REQ-19 · AI 호출 중 저장된 본문을 결과 저장이 덮지 않는다")
    void resultDoesNotOverwriteConcurrentSave() {
        String sub = "000777.writing.ai.race." + SEQ.incrementAndGet();
        User u = appleUsers.register(sub);
        u.consentToAi(CONSENT_AT);
        User me = userRepository.saveAndFlush(u);
        LocalDate day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        String month = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).toString();
        boolean hadDaily = dailyTotalRepository.existsByUsageDate(day);
        boolean hadMonth = spendRepository.findBySpendMonth(month).isPresent();
        try {
            Story d = note(me, true, "처음 쓴 본문입니다");
            given(assistant.ask(anyString())).willAnswer(inv -> {
                storyService.editNote(me, d.getId(), null, "AI를 기다리는 동안 이어 쓴 본문", 0, null); // 다른 요청의 PUT
                return answer("어떤 투쟁이었나요?");
            });

            aiService.run(me, d.getId(), Step.ASK, 0, null);

            Story after = storyRepository.findById(d.getId()).orElseThrow();
            assertThat(after.getText()).isEqualTo("AI를 기다리는 동안 이어 쓴 본문");
            assertThat(after.getRevision()).isEqualTo(1);
            assertThat(QuestionLog.parse(after.getAiLog()).rounds()).hasSize(1); // 양성 대조 — AI 열은 저장됐다
        } finally {
            accountService.deleteAppleVerifiedAccount(userRepository.findById(me.getId()).orElseThrow(), sub);
            if (!hadDaily) {
                dailyTotalRepository.findByUsageDate(day).ifPresent(dailyTotalRepository::delete);
            } else {
                dailyTotalRepository.refund(day);
            }
            if (!hadMonth) {
                spendRepository.findBySpendMonth(month).ifPresent(spendRepository::delete);
            }
        }
    }

    @Test
    @DisplayName("REQ-19 · 성공하면 질문 기록에 라운드가 하나 늘어난다")
    void successAppendsRound() {
        User u = user(true);
        Story d = note(u, true, null);
        rounds(d, 2);
        given(assistant.ask(anyString())).willReturn(answer("새 질문인가요?"));

        AiView view = aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null);

        assertThat(view.questions().rounds()).hasSize(3);
        assertThat(view.questions().rounds().get(2).questions()).extracting(Question::text).containsExactly("새 질문인가요?");
        assertThat(QuestionLog.parse(storyRepository.findById(d.getId()).orElseThrow().getAiLog()).rounds()).hasSize(3);
        assertThat(view.aiUpdatedAt()).isNotNull();
    }

    // ── REQ-20 전제조건 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-20 · 재료 0개 ASK는 400이고 몫이 그대로")
    void askWithoutMaterialIs400() {
        User u = user(true);
        Story d = note(u, false, null);

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        verify(assistant, never()).ask(anyString());
    }

    @Test
    @DisplayName("REQ-20 · 공백 뺀 29자 본문 FOLLOW는 400이고 30자는 통과")
    void followNeedsThirtyChars() {
        User u = user(true);
        Story short29 = note(u, true, "  " + "가".repeat(29) + "\n ");
        Story ok30 = note(u, true, " " + "나".repeat(30) + " ");
        given(assistant.follow(anyString(), anyList(), any(), anyString())).willReturn(answer("왜 그랬나요?"));

        assertThat(statusOf(() -> aiService.run(u, short29.getId(), Step.FOLLOW, short29.getRevision(), null)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(aiService.run(u, ok30.getId(), Step.FOLLOW, ok30.getRevision(), null).questions().rounds()).hasSize(1);
    }

    @Test
    @DisplayName("REQ-20 · 라운드 8개인 글의 AI 요청은 400이고 몫이 그대로(7개면 통과)")
    void roundCap() {
        User u = user(true);
        Story eight = note(u, true, null);
        rounds(eight, 8);
        Story seven = note(u, true, null);
        rounds(seven, 7);
        given(assistant.ask(anyString())).willReturn(answer("여덟 번째인가요?"));

        assertThat(statusOf(() -> aiService.run(u, eight.getId(), Step.ASK, eight.getRevision(), null)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(aiService.run(u, seven.getId(), Step.ASK, seven.getRevision(), null).questions().rounds()).hasSize(8);
    }

    @Test
    @DisplayName("REQ-20 · focus 201자는 400")
    void focusTooLong() {
        User u = user(true);
        Story d = note(u, true, "가".repeat(40));
        given(assistant.follow(anyString(), anyList(), any(), anyString())).willReturn(answer("왜 그랬나요?"));

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.FOLLOW, d.getRevision(), "가".repeat(201))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(aiService.run(u, d.getId(), Step.FOLLOW, d.getRevision(), "가".repeat(200)).questions().rounds()).hasSize(1);
    }

    // ── REQ-21 하루 몫 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-21 · WRITE 21번째 호출은 429이고 「오전 9시」 안내")
    void twentyFirstIs429() {
        User u = user(true);
        Story d = note(u, true, null);
        for (int i = 0; i < Kind.WRITE.max(); i++) {
            assertThat(usageService.tryConsumeBoth(u, clock.instant(), Kind.WRITE)).isEqualTo(StudyAiUsageService.Grant.OK);
        }
        assertThat(Kind.WRITE.max()).isEqualTo(20);

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(reasonOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null))).contains("오전 9시");
        verify(assistant, never()).ask(anyString());
    }

    @Test
    @DisplayName("REQ-21 · 전역 하루 상한 소진이면 503이고 내 몫은 환불")
    void globalCapIs503() {
        User u = user(true);
        Story d = note(u, true, null);
        while (usageService.tryConsumeGlobal(clock.instant())) {
            // 전역 몫을 다 쓴다(이 테스트 트랜잭션 안 — 끝나면 롤백)
        }

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(reasonOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null))).contains("요청이 많아");
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        verify(assistant, never()).ask(anyString());
    }

    // ── REQ-22 월 예산 · 비용 ─────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-22 · 월 누적이 상한 이상이면 호출 전에 503(어댑터 호출 0)")
    void monthlyBudgetBlocks() {
        User u = user(true);
        Story d = note(u, true, null);
        budgetService.add(clock.instant(), 20_000_000L); // 기본 상한 $20

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(reasonOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null))).contains("이번 달");
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        verify(assistant, never()).ask(anyString());
    }

    @Test
    @DisplayName("REQ-22 · 가드가 결과를 비워도 비용은 월 누적과 글에 더해진다")
    void guardEmptiedStillCosts() {
        User u = user(true);
        Story d = note(u, true, null);
        long before = monthSpend();
        int globalBefore = globalUsed();
        given(assistant.ask(anyString())).willReturn(answer("이렇게 써 보세요.")); // 물음표 없음 → 가드가 비운다

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        long cost = AiPrice.microUsd(MODEL, new Usage(1_000, 0, 0, 500));
        assertThat(cost).isPositive();
        assertThat(monthSpend() - before).isEqualTo(cost);
        Story after = storyRepository.findById(d.getId()).orElseThrow();
        assertThat(after.getAiCostMicroUsd()).isEqualTo(cost);
        assertThat(QuestionLog.parse(after.getAiLog()).rounds()).isEmpty();
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(globalUsed()).as("전역 하루 몫도 돌려준다").isEqualTo(globalBefore);
    }

    @Test
    @DisplayName("REQ-22 · 성공한 호출의 usage 원가가 월 누적과 글에 더해진다")
    void successCostsMonthAndNote() {
        User u = user(true);
        Story d = note(u, true, null);
        long before = monthSpend();
        given(assistant.ask(anyString())).willReturn(answer("새 질문인가요?"));

        aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null);

        long cost = AiPrice.microUsd(MODEL, new Usage(1_000, 0, 0, 500));
        assertThat(cost).isPositive();
        assertThat(monthSpend() - before).isEqualTo(cost);
        assertThat(storyRepository.findById(d.getId()).orElseThrow().getAiCostMicroUsd()).isEqualTo(cost);
    }

    @Test
    @DisplayName("REQ-22 · 응답 없이 시간 초과된 호출은 보수 추정치를 월 누적과 글에 더한다")
    void noResponseAddsEstimate() {
        User u = user(true);
        Story d = note(u, true, "가".repeat(40));
        long before = monthSpend();
        ArgumentCaptor<String> material = ArgumentCaptor.forClass(String.class);
        given(assistant.follow(material.capture(), anyList(), any(), anyString())).willReturn(fail(Failure.NO_RESPONSE, null));

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.FOLLOW, d.getRevision(), "고른 질문?")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        int chars = ClaudeWritingAssistant.sentChars(Step.FOLLOW,
                ClaudeWritingAssistant.userBlocks(Step.FOLLOW, material.getValue(), List.of(), "고른 질문?", "가".repeat(40)));
        long estimate = AiPrice.estimateMicroUsd(MODEL, chars, ClaudeWritingAssistant.FOLLOW_MAX_TOKENS);
        assertThat(estimate).isPositive();
        assertThat(monthSpend() - before).isEqualTo(estimate);
        assertThat(storyRepository.findById(d.getId()).orElseThrow().getAiCostMicroUsd()).isEqualTo(estimate);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
    }

    @Test
    @DisplayName("REQ-22 · HTTP 오류 응답은 비용 0")
    void httpErrorCostsNothing() {
        User u = user(true);
        Story d = note(u, true, null);
        long before = monthSpend();
        given(assistant.ask(anyString())).willReturn(fail(Failure.UNAVAILABLE, null));

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(monthSpend()).isEqualTo(before);
        assertThat(storyRepository.findById(d.getId()).orElseThrow().getAiCostMicroUsd()).isZero();
    }

    // ── REQ-27 거절 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-27 · refusal은 REFUSED로 422 안내이고 몫을 환불한다")
    void refusalIs422() {
        User u = user(true);
        Story d = note(u, true, null);
        long before = monthSpend();
        given(assistant.ask(anyString())).willReturn(fail(Failure.REFUSED, new Usage(1_000, 0, 0, 20)));

        assertThat(statusOf(() -> aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null)))
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(remaining(u)).isEqualTo(Kind.WRITE.max());
        assertThat(monthSpend() - before).as("거절도 과금된다 — 비용은 기록").isEqualTo(AiPrice.microUsd(MODEL, new Usage(1_000, 0, 0, 20)));
    }

    // ── REQ-46 질문 기록 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-46 · FOLLOW 어댑터 입력의 앞서 받은 질문은 기록의 모든 라운드 질문을 먼저 받은 것부터 담는다")
    void priorQuestionsInOrder() {
        User u = user(true);
        Story d = note(u, true, "가".repeat(40));
        rounds(d, 2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> prior = ArgumentCaptor.forClass(List.class);
        given(assistant.follow(anyString(), prior.capture(), any(), anyString())).willReturn(answer("왜 그랬나요?"));

        aiService.run(u, d.getId(), Step.FOLLOW, d.getRevision(), null);

        assertThat(prior.getValue()).containsExactly("r1-a?", "r1-b?", "r2-a?", "r2-b?");
    }

    @Test
    @DisplayName("REQ-46 · 라운드 기록에 step·focus·bodyChars·questions가 남고 bodyChars는 보낸 본문 길이다")
    void roundRecordsFields() {
        User u = user(true);
        String body = "회사를 그만두던 날 나는 처음으로 내 껍질을 보았다. 그날의 공기가 기억난다.";
        Story d = note(u, true, body);
        ArgumentCaptor<String> sentBody = ArgumentCaptor.forClass(String.class);
        given(assistant.follow(anyString(), anyList(), any(), sentBody.capture())).willReturn(answer("그날 공기는 어땠나요?"));

        AiView view = aiService.run(u, d.getId(), Step.FOLLOW, d.getRevision(), "어떤 장면이 겹쳤나요?");

        QuestionLog.Round r = view.questions().rounds().get(0);
        assertThat(r.step()).isEqualTo(Step.FOLLOW);
        assertThat(r.focus()).isEqualTo("어떤 장면이 겹쳤나요?");
        assertThat(r.bodyChars()).isEqualTo(sentBody.getValue().length()).isEqualTo(body.length());
        assertThat(r.questions()).extracting(Question::text).containsExactly("그날 공기는 어땠나요?");
        assertThat(r.at()).isNotNull();
    }

    // ── REQ-47 사용자 글은 읽기 전용 ──────────────────────────────────────────

    @Test
    @DisplayName("REQ-47 · AI 단계 뒤 생각·revision이 그대로이고 질문 기록만 바뀐다")
    void bodyTitleRevisionUntouched() {
        User u = user(true);
        Story d = note(u, true, "내가 쓴 본문 그대로 남아야 하는 글입니다. 서른 자를 넘깁니다.");
        String beforeJson = d.getAiLog();
        given(assistant.follow(anyString(), anyList(), any(), anyString()))
                .willReturn(answer("「대신 써 준 문장」을 넣어 볼까요?", "AI가 지어낸 제목은 무엇인가요?"));

        aiService.run(u, d.getId(), Step.FOLLOW, d.getRevision(), null);

        Story after = storyRepository.findById(d.getId()).orElseThrow();
        assertThat(after.getText()).isEqualTo("내가 쓴 본문 그대로 남아야 하는 글입니다. 서른 자를 넘깁니다.")
                .doesNotContain("AI가 지어낸 제목");
        assertThat(after.getRevision()).isZero();
        assertThat(after.getAiLog()).as("양성 대조 — 질문 기록은 바뀌었다").isNotEqualTo(beforeJson)
                .contains("AI가 지어낸 제목은 무엇인가요?");
    }

    // ── REQ-228 · J34 여백 AI 재료(책생글 M1) ────────────────────────────────────

    @Test
    @DisplayName("REQ-228 · 책 없는 쪽지 ASK 재료에 다른 쪽지 본문 0 · 책 칸 빔")
    void booklessNoteMaterialIsThatNoteOnly() {
        User u = user(true);
        Book book = bookRepository.saveAndFlush(Book.register(u, "데미안", "헤르만 헤세", null, null, null, null, BookStatus.READING));
        storyRepository.saveAndFlush(Story.of(u, "책 쪽지의 생각", book, null, "새는 알에서 나오려고 투쟁한다"));
        storyRepository.saveAndFlush(Story.of(u, "다른 책 없는 쪽지", null, null));
        Story d = storyRepository.saveAndFlush(Story.of(u, "책 없이 끄적인 생각", null, null));
        ArgumentCaptor<String> material = ArgumentCaptor.forClass(String.class);
        given(assistant.ask(material.capture())).willReturn(answer("무엇이 남았나요?"));

        aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null);

        assertThat(material.getValue())
                .contains("책 없이 끄적인 생각")
                .startsWith("[책] 없음")
                .doesNotContain("다른 책 없는 쪽지", "책 쪽지의 생각", "새는 알에서", "데미안", "헤르만 헤세");
    }

    @Test
    @DisplayName("M1 · 책 쪽지 ASK 재료 = 지금 쪽지 먼저 · 같은 책 다른 쪽지는 뒤 · 다른 책 쪽지는 0(J34)")
    void bookNoteMaterialPutsCurrentFirst() {
        User u = user(true);
        Book book = bookRepository.saveAndFlush(Book.register(u, "데미안", "헤르만 헤세", null, null, null, null, BookStatus.READING));
        Book other = bookRepository.saveAndFlush(Book.register(u, "싯다르타", "헤르만 헤세", null, null, null, null, BookStatus.READING));
        Story d = storyRepository.saveAndFlush(Story.of(u, "먼저 쓴 지금 쪽지", book, null));
        storyRepository.saveAndFlush(Story.of(u, "나중에 쓴 같은 책 쪽지", book, null));
        storyRepository.saveAndFlush(Story.of(u, "다른 책 쪽지", other, null));
        ArgumentCaptor<String> material = ArgumentCaptor.forClass(String.class);
        given(assistant.ask(material.capture())).willReturn(answer("무엇이 남았나요?"));

        aiService.run(u, d.getId(), Step.ASK, d.getRevision(), null);

        String sent = material.getValue();
        assertThat(sent).contains("[책] 데미안").doesNotContain("다른 책 쪽지");
        assertThat(sent.indexOf("먼저 쓴 지금 쪽지")).isNotNegative()
                .isLessThan(sent.indexOf("나중에 쓴 같은 책 쪽지"));
    }
}
