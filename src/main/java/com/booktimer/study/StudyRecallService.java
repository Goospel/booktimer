package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.study.ClaudeStudyAssistant.AiResult;
import com.booktimer.study.ClaudeStudyAssistant.Failure;
import com.booktimer.study.ClaudeStudyAssistant.RecallAnalysis;
import com.booktimer.study.ClaudeStudyAssistant.RecallInput;
import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 백지복습 유스케이스 — 저장(하루 한 장 upsert)과 분석(승인 · 상한 · 외부 호출 · 환불).
 *
 * <p>{@link #analyze}의 <b>순서가 이 클래스의 설계</b>다:
 * ① 승인 게이트 → ② 대상 확인 → ③ 재분석 차단 → ④ 키 확인 → ⑤ 상한 선점 → ⑥ 외부 호출 → ⑦ 실패면 환불.
 * 게이트가 맨 앞인 것은 두 가지를 위해서다 — 미승인 사용자에게 「AI가 꺼졌다」가 아니라 「승인이 필요하다」를
 * 말해야 하고, 승인되지 않은 요청이 상한 행을 만들거나 어댑터를 부르면 안 된다.
 *
 * <p>{@code SUPPORTS}로 도는 것은 <b>외부 호출을 트랜잭션 밖에 두기 위해서</b>다({@code
 * ReadingPersonalityService.reanalyze} 선례) — 90초짜리 호출이 DB 커넥션을 붙잡고 있으면 안 된다.
 * 쓰기는 리포지터리 메서드가 자기 트랜잭션으로 처리한다.
 */
@Service
@Transactional(propagation = Propagation.SUPPORTS)
public class StudyRecallService {

    private static final Logger log = LoggerFactory.getLogger(StudyRecallService.class);

    // Boot 4 모듈러 autoconfig라 ObjectMapper 빈이 없다(T-022) — 자체 인스턴스(스레드 안전·재사용).
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final StudyRecallRepository recallRepository;
    private final StudyAiAccessService accessService;
    private final StudyAiUsageService usageService;
    private final ClaudeStudyAssistant assistant;
    private final Clock clock;

    public StudyRecallService(StudyRecallRepository recallRepository,
                              StudyAiAccessService accessService,
                              StudyAiUsageService usageService,
                              ClaudeStudyAssistant assistant,
                              Clock clock) {
        this.recallRepository = recallRepository;
        this.accessService = accessService;
        this.usageService = usageService;
        this.assistant = assistant;
        this.clock = clock;
    }

    public Optional<StudyRecall> find(User user, LocalDate date) {
        return recallRepository.findByUserAndRecallDate(user, date);
    }

    /**
     * 달력이 그릴 구간의 글들 — 전달 말일부터 당기는 것은 호출부 몫이다({@code hasQuestions} 표식이
     * 다음날에 서기 때문, 리포지터리 javadoc).
     */
    public List<StudyRecall> between(User user, LocalDate from, LocalDate to) {
        return recallRepository.findByUserAndRecallDateBetweenOrderByRecallDateAsc(user, from, to);
    }

    /**
     * 그날의 글을 저장한다(없으면 새로, 있으면 덮어쓰기).
     *
     * <p><b>승인과 무관하다</b> — AI를 쓰지 않는 글쓰기라 막을 이유가 없고, 그래야 「AI 없이 저장만」이라는
     * 폴백이 실제로 성립한다.
     *
     * @throws IllegalArgumentException 미래 날짜 · 빈 본문 · 길이 위반(문구가 그대로 400 본문)
     */
    @Transactional
    public StudyRecall save(User user, LocalDate date, StudyBook book, String subject, String scope,
                            String body, StudyRecall.Source source) {
        if (date == null) {
            throw new IllegalArgumentException("날짜가 없어요");
        }
        if (date.isAfter(StudyDates.today(user, clock))) {
            throw new IllegalArgumentException("아직 오지 않은 날은 쓸 수 없어요");
        }
        StudyRecall recall = recallRepository.findByUserAndRecallDate(user, date)
                .map(existing -> {
                    existing.rewrite(book, subject, scope, body, source);
                    return existing;
                })
                .orElseGet(() -> StudyRecall.of(user, date, book, subject, scope, body, source));
        return recallRepository.save(recall);
    }

    /**
     * 그날의 글을 분석한다 — 정리 · 구멍 · 다음날 복습문제.
     *
     * @throws ResponseStatusException 403 미승인 · 404 없음 · 409 이미 분석됨 · 429 오늘 몫 소진 ·
     *                                 503 AI 꺼짐·응답 없음
     */
    public StudyRecall analyze(User user, LocalDate date) {
        accessService.requireApproved(user); // ① 게이트가 가장 앞 — 키 검사·상한 선점보다 먼저다

        StudyRecall recall = find(user, date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "쓴 글을 찾을 수 없습니다"));
        if (recall.isAnalyzed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 분석한 글이에요");
        }
        if (!assistant.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능이 꺼져 있어요");
        }

        LocalDate today = StudyDates.today(user, clock);
        if (!usageService.tryConsume(user, today, Kind.ANALYZE)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "오늘 몫을 다 썼어요 — 내일 다시 해 주세요");
        }

        AiResult<RecallAnalysis> result = assistant.analyzeRecall(
                new RecallInput(recall.getSubject(), recall.getScopeText(), recall.getBody()));
        if (!result.ok()) {
            usageService.refund(user, today, Kind.ANALYZE);
            throw failure(result.failure());
        }
        Optional<RecallAnalysis> analysis = ClaudeStudyAssistant.normalize(result.value());
        if (analysis.isEmpty()) {
            // 형식은 맞는데 담을 값이 없다 — 저장하면 화면이 빈 칸으로 채워진다. 실패로 취급하고 환불한다.
            log.warn("Claude 분석 결과가 비어 저장하지 않는다 — user={}", user.getId());
            usageService.refund(user, today, Kind.ANALYZE);
            throw failure(Failure.UNAVAILABLE);
        }
        recall.applyAnalysis(analysis.get().summary(), encode(analysis.get().holes()),
                encode(analysis.get().questions()), assistant.model(), clock.instant());
        return recallRepository.save(recall);
    }

    /** 오늘 남은 분석 몫 — 화면이 버튼 옆에 그린다. */
    public int remainingAnalyze(User user) {
        return usageService.remaining(user, StudyDates.today(user, clock), Kind.ANALYZE);
    }

    /**
     * 어댑터의 실패 갈래를 HTTP로 옮긴다.
     *
     * <p>{@code BAD_INPUT}이 「사진」을 말하지 않는 것에 유의 — 이 문은 글만 다룬다. 사진 문구는 전사
     * 엔드포인트가 붙는 판에서 그쪽이 든다(문구는 <b>엔드포인트마다</b> 다르다).
     */
    private static ResponseStatusException failure(Failure failure) {
        return switch (failure) {
            case DISABLED -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능이 꺼져 있어요");
            case RATE_LIMITED -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요");
            case BAD_INPUT -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 글은 분석할 수 없어요");
            case UNAVAILABLE -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 응답을 받지 못했어요 — 글은 저장돼 있어요");
        };
    }

    /** 목록을 JSON 배열 문자열로. 실패할 자리가 아니지만(문자열 배열) 조용히 null을 남기지 않는다. */
    static String encode(List<String> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            log.warn("분석 결과 직렬화 실패: {}", e.toString());
            return "[]";
        }
    }

    /** 저장된 JSON 배열을 읽는다. 깨진 값은 빈 목록으로 — 옛 행 하나가 화면을 깨뜨리지 않는다. */
    public static List<String> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, STRING_LIST);
        } catch (Exception e) {
            log.warn("분석 결과 파싱 실패: {}", e.toString());
            return List.of();
        }
    }
}
