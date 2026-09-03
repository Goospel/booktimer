package com.booktimer.study;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Claude API 어댑터 — 공부 화면의 AI 능력 한 곳.
 *
 * <p>{@code GeminiReadingPersonalityNarrator}와 같은 규율이다: 키를 {@code @Value}로 받아
 * {@link #isEnabled()} 게이트로 없으면 아예 나가지 않고, 타임아웃을 명시하고, 프롬프트 조립·응답 정제는
 * <b>정적 메서드</b>로 떼어 네트워크 없이 잰다. 실패는 예외로 새지 않고 {@link Failure}로 격리된다 —
 * 외부 장애가 화면을 깨뜨리면 안 된다.
 *
 * <p><b>승인 게이트는 여기 없다.</b> 이 클래스는 {@code User}를 모른다 — 누가 부를 수 있는지는
 * {@link StudyAiAccessService#requireApproved}가 호출부 첫 줄에서 정한다(가드 하나, 호출부 여럿).
 *
 * <p>인터페이스를 만들지 않은 것은 공급자 교체 계획이 없기 때문이다(테스트는 {@code @MockitoBean}).
 */
@Component
public class ClaudeStudyAssistant {

    private static final Logger log = LoggerFactory.getLogger(ClaudeStudyAssistant.class);
    private static final String NOT_CONFIGURED = "not-configured";

    /** 구멍·복습문제의 최대 개수 — 스무 개짜리 배열이 화면을 덮지 않게 자른다. */
    static final int MAX_LIST_ITEMS = 10;

    private static final long ANALYZE_MAX_TOKENS = 8192;
    private static final long TRANSCRIBE_MAX_TOKENS = 4096;

    /** 한 요청에 넘길 수 있는 사진 수 — 컨트롤러의 400 판정과 <b>같은 상수</b>를 본다. */
    public static final int MAX_IMAGES = 3;

    /**
     * 전사 시스템 프롬프트 — <b>옮겨 적기</b>만 시킨다.
     *
     * <p>이 프롬프트의 요점은 「고치지 마라」다. 모델이 손글씨를 읽으며 문장을 다듬으면, 사용자는 자기가
     * 쓰지 않은 문장을 자기 글로 알고 저장하게 되고 그 글이 다음 판의 분석 근거가 된다 — 확인 단계가
     * 있어도 사람은 그럴듯한 문장을 잘 지나친다. 그래서 못 읽은 글자는 지어내는 대신 {@code [?]}로 남긴다.
     */
    private static final String TRANSCRIBE_SYSTEM = """
            사진에 찍힌 것은 종이에 손으로 쓴 공부 메모다. 보이는 대로 옮겨 적는다.

            반드시 지킬 것:
            - 내용을 고치거나 보태거나 요약하지 않는다. 맞춤법도 손대지 않는다.
            - 못 읽는 글자는 지어내지 말고 [?] 로 남긴다.
            - 취소선으로 지운 부분은 옮기지 않는다.
            - 줄바꿈·번호·들여쓰기·화살표(→)는 텍스트로 그대로 살린다.
            - 사진이 공부 메모가 아니거나 글씨를 전혀 못 읽으면 unreadable=true 로 하고 text 는 비운다.
            - 사진이 여러 장이면 올라온 순서대로 이어 붙인다.
            """;

    /**
     * 분석 시스템 프롬프트 — 환각 억제가 목적이다. 「적힌 것만 근거」·「범위 울타리」·「확실하지 않으면
     * 넣지 않는다」 셋이 규칙의 전부이고, 나머지는 출력 형태다.
     */
    private static final String ANALYZE_SYSTEM = """
            당신은 백지복습(빈 종이에 기억나는 것을 쏟아내는 공부법) 결과를 봐 주는 튜터다.
            사용자가 오늘 쓴 글과, 그 글이 다루기로 한 「범위」가 주어진다.

            반드시 지킬 것:
            - **적힌 것만 근거로 삼는다.** 글에 없는 사실을 새로 만들어 넣지 않는다.
            - summary(정리): 사용자가 적은 내용을 읽기 좋게 구조화한다. 새 지식을 보태지 않는다.
            - holes(구멍): 「범위」에 명시된 주제 안에서 **빠져 있거나 틀린** 핵심 항목만 고른다.
              범위가 주어지지 않았으면, 적힌 내용 안에서 설명이 불완전한 부분만 짚는다.
              확실하지 않으면 넣지 않는다 — 없으면 빈 배열이 정답이다.
            - questions(복습문제): 내일 풀 문제를 3~7개 만든다. holes를 먼저 겨눈다.
            - 전부 한국어 존댓말로 쓴다.
            """;

    private final String model;

    /**
     * 키가 있을 때만 만든다 — 없으면 {@code null}이고 {@link #isEnabled()}가 그 앞을 막는다.
     *
     * <p>키를 <b>필드로 들고 있지 않는 것</b>이 의도다: 클라이언트가 이미 쥐고 있어 다시 볼 일이 없고,
     * 시크릿을 필드에 남겨 두면 힙 덤프·디버거·무심한 로그에 실릴 자리가 하나 더 생긴다.
     */
    private final AnthropicClient client;

    public ClaudeStudyAssistant(@Value("${booktimer.claude.api-key:not-configured}") String apiKey,
                                @Value("${booktimer.claude.model:claude-sonnet-5}") String model) {
        this.model = model;
        this.client = hasRealKey(apiKey)
                ? AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        // 90초 — 분석 한 번이 수십 초 걸릴 수 있고, 요청 스레드가 그동안 기다린다.
                        // 재시도는 1회까지만(어댑터가 붙잡고 늘어지면 상한·환불 흐름이 늘어진다).
                        .timeout(Duration.ofSeconds(90))
                        .maxRetries(1)
                        .build()
                : null;
    }

    public boolean isEnabled() {
        return client != null;
    }

    /** 저장되는 {@code model} 컬럼의 출처 — 「무슨 모델이 쓴 분석인가」를 남긴다. */
    public String model() {
        return model;
    }

    /**
     * 백지복습 글을 분석한다 — 정리 · 구멍 · 복습문제.
     *
     * <p>정제({@link #normalize})는 <b>여기서 하지 않는다</b>. 정제는 「모델이 뭘 보내든 화면에 담을 만한
     * 모양으로 만든다」는 규칙이고, 그 규칙이 실제로 걸리는지는 호출부(서비스) 경로에서 재야 한다 —
     * 어댑터가 목으로 대체되는 테스트에서 정제까지 함께 사라지면 그 규칙엔 계측기가 없어진다.
     *
     * @return 성공이면 모델이 낸 그대로, 실패면 {@link Failure}(예외는 밖으로 새지 않는다)
     */
    public AiResult<RecallAnalysis> analyzeRecall(RecallInput in) {
        if (!isEnabled() || in == null) {
            return AiResult.fail(Failure.DISABLED);
        }
        return call("analyze", MessageCreateParams.builder()
                .model(model)
                .maxTokens(ANALYZE_MAX_TOKENS)
                // 시스템 프롬프트는 매 호출 같은 문자열이라 캐시를 건다. 최소 캐시 길이 미만이면
                // 조용히 캐시되지 않을 뿐 요청은 정상이다(무해 — U-7).
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(ANALYZE_SYSTEM)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .outputConfig(RecallAnalysis.class)
                .addUserMessage(recallUserPrompt(in))
                .build());
    }

    /**
     * 사진에 손으로 쓴 메모를 <b>옮겨 적는다</b>(고치지 않는다).
     *
     * <p>바이트는 여기서 base64 문자열이 되어 요청에 실릴 뿐 <b>어디에도 저장되지 않는다</b> — 이 메서드가
     * 끝나면 남는 것은 응답 텍스트뿐이고, 그 텍스트마저 저장 여부는 사용자가 확인한 뒤에 정한다.
     *
     * @param images 1~{@value #MAX_IMAGES}장. 장수·형식·크기 검증은 호출부(서비스)가 이미 끝냈다
     */
    public AiResult<Transcript> transcribe(List<ImagePart> images) {
        if (!isEnabled() || images == null || images.isEmpty()) {
            return AiResult.fail(Failure.DISABLED);
        }
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (ImagePart image : images) {
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(image.bytes()))
                            .mediaType(mediaTypeOf(image.mediaType()))
                            .build())
                    .build()));
        }
        // 지시 블록은 사진 <b>뒤</b>에 둔다 — 모델이 이미지를 먼저 본 뒤 지시를 읽는 순서가 된다.
        blocks.add(ContentBlockParam.ofText("이 사진들에 적힌 글을 순서대로 옮겨 적어 주세요."));

        return call("transcribe", MessageCreateParams.builder()
                .model(model)
                .maxTokens(TRANSCRIBE_MAX_TOKENS)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(TRANSCRIBE_SYSTEM)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .outputConfig(Transcript.class)
                .addUserMessageOfBlockParams(blocks)
                .build());
    }

    /**
     * 한 번의 호출 — 두 능력이 공유하는 몸통.
     *
     * <p>여기 모아 둔 것이 <b>실패의 갈래</b>다: 잘린 응답 · 레이트리밋 · 요청 거부 · 그 밖의 장애.
     * 능력마다 이 사다리를 따로 쓰면 한쪽에서만 429를 500으로 새게 하기 쉽다.
     */
    private <T> AiResult<T> call(String kind, StructuredMessageCreateParams<T> params) {
        long started = System.currentTimeMillis();
        try {
            StructuredMessage<T> message = client.messages().create(params);

            // 끝까지 못 쓴 응답(MAX_TOKENS·REFUSAL)은 잘린 결과라 성공으로 치지 않는다.
            Optional<StopReason> stop = message.stopReason();
            if (stop.isPresent() && !StopReason.END_TURN.equals(stop.get())) {
                log.warn("Claude {} 중단 — stopReason={}", kind, stop.get());
                return AiResult.fail(Failure.UNAVAILABLE);
            }
            T parsed = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElse(null);
            logCall(kind, started, message);
            return parsed == null ? AiResult.fail(Failure.UNAVAILABLE) : AiResult.ok(parsed);
        } catch (RateLimitException e) {
            log.warn("Claude {} 레이트리밋: {}", kind, e.toString());
            return AiResult.fail(Failure.RATE_LIMITED);
        } catch (BadRequestException e) {
            log.warn("Claude {} 요청 거부: {}", kind, e.toString());
            return AiResult.fail(Failure.BAD_INPUT);
        } catch (Exception e) {
            // 키·본문·사진은 로그에 남기지 않는다(Gemini 선례) — toString만.
            log.warn("Claude {} 실패: {}", kind, e.toString());
            return AiResult.fail(Failure.UNAVAILABLE);
        }
    }

    /** 지연·캐시 실측(U-6·U-7)의 유일한 계측 지점. 본문·사진은 찍지 않는다. */
    private void logCall(String kind, long startedMillis, StructuredMessage<?> message) {
        log.info("claude {} {}ms cacheRead={} in={} out={}", kind,
                System.currentTimeMillis() - startedMillis,
                message.usage().cacheReadInputTokens().orElse(0L),
                message.usage().inputTokens(),
                message.usage().outputTokens());
    }

    /**
     * 우리가 허용한 세 형식만 SDK 상수로 옮긴다.
     *
     * <p>검증은 서비스가 이미 했으므로 여기 오는 값은 셋 중 하나다 — 그럼에도 기본값을 두는 대신
     * {@link IllegalArgumentException}으로 터뜨리는 것은, 검증을 고치며 여기를 잊으면 <b>heic를 jpeg라고
     * 우기며</b> 외부에 나가기 때문이다.
     */
    private static Base64ImageSource.MediaType mediaTypeOf(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            default -> throw new IllegalArgumentException("지원하지 않는 사진 형식: " + contentType);
        };
    }

    // ── 순수(정적) — 네트워크 없이 단위테스트하는 절반 ──

    /**
     * 분석 요청의 user 메시지. 범위가 비면 그 사실을 <b>명시</b>한다 — 빈 줄을 남기면 모델이 울타리를
     * 제 마음대로 넓혀 「안 배운 것」을 구멍으로 집는다.
     */
    static String recallUserPrompt(RecallInput in) {
        String subject = blankToNull(in.subject());
        String scope = blankToNull(in.scope());
        // ⚠️ 본문·범위·과목은 사용자가 친 글이거나 사진에서 전사된 텍스트라 신뢰할 수 없다 — 그 안에
        // 「[범위]」 같은 라벨을 적어 넣어 이 템플릿의 섹션을 위조할 수 있다. 지금은 폭발 반경이 자기
        // 분석 결과뿐이라(툴·외부 호출·다른 사용자로 새는 경로가 없다) 막지 않았다. 여기에 툴 사용이나
        // 유출 경로가 붙는 날에는 구분자·이스케이프(또는 본문을 별도 블록으로 분리)를 먼저 넣어야 한다.
        return """
                [과목] %s
                [범위] %s
                [오늘 쓴 글]
                %s
                """.formatted(
                subject == null ? "(적지 않음)" : subject,
                scope == null ? "범위 없음 — 글에 적힌 내용 안에서만 판단해 주세요" : scope,
                in.body() == null ? "" : in.body());
    }

    /**
     * 모델 응답을 화면·DB에 담을 모양으로 다듬는다.
     *
     * <p>비는 경우가 정상 동작이라 {@link Optional}이다 — 정리(summary)가 없는 분석은 저장할 값이 없다
     * (구멍·문제만 있는 결과는 「뭘 썼는지」를 잃은 반쪽이다).
     */
    static Optional<RecallAnalysis> normalize(RecallAnalysis raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String summary = raw.summary() == null ? "" : raw.summary().strip();
        if (summary.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RecallAnalysis(summary, cleanList(raw.holes()), cleanList(raw.questions())));
    }

    /**
     * 전사 결과를 화면에 담을 모양으로 다듬는다 — 앞뒤 공백만 턴다.
     *
     * <p>줄바꿈은 <b>내용</b>이라 손대지 않는다(번호 목록·화살표가 줄로 서 있는 것이 손메모의 형태다).
     *
     * <p>「읽은 글도 없고 unreadable도 아니다」는 모델이 답을 못 낸 것이라 빈 결과다 — 그대로 200을
     * 돌려주면 화면이 <b>빈 textarea를 「전사 완료」라고</b> 말한다. 반면 unreadable=true는 정상적인 답이라
     * 빈 글이어도 값이 있다(「못 읽었어요」라고 말할 근거).
     */
    static Optional<Transcript> normalize(Transcript raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.text() == null ? "" : raw.text().strip();
        if (raw.unreadable()) {
            return Optional.of(new Transcript(text, true));
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(new Transcript(text, false));
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.strip();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
            if (cleaned.size() == MAX_LIST_ITEMS) {
                break;
            }
        }
        return List.copyOf(cleaned);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean hasRealKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank() && !NOT_CONFIGURED.equals(apiKey);
    }

    // ── 형(型) ──

    /** 실패의 갈래 — 화면 문구와 HTTP 상태가 여기서 갈린다(호출부가 옮긴다). */
    public enum Failure {
        /** 키가 없다(외부에 나가지도 않았다). */
        DISABLED,
        /** 429 — 잠시 후 다시. */
        RATE_LIMITED,
        /** 요청 자체가 거부됐다(사진 형식 등 — 사진 경로가 붙는 판에서 주로 쓰인다). */
        BAD_INPUT,
        /** 그 밖의 장애·파싱 실패·잘린 응답. */
        UNAVAILABLE
    }

    /**
     * 성공값 또는 실패 사유 — 둘 중 하나만 채워진다.
     *
     * <p>{@code Optional}이 아닌 이유는 <b>왜 실패했는가</b>가 화면 문구를 가르기 때문이다(꺼짐 · 혼잡 ·
     * 장애가 서로 다른 안내다).
     */
    public record AiResult<T>(T value, Failure failure) {

        public static <T> AiResult<T> ok(T value) {
            return new AiResult<>(value, null);
        }

        public static <T> AiResult<T> fail(Failure failure) {
            return new AiResult<>(null, failure);
        }

        public boolean ok() {
            return failure == null && value != null;
        }
    }

    /** 분석 입력 — 어댑터는 엔티티를 모른다(호출부가 옮겨 담는다). */
    public record RecallInput(String subject, String scope, String body) {
    }

    /**
     * 보낼 사진 한 장 — 바이트를 <b>메모리로만</b> 들고 다닌다.
     *
     * <p>{@code MultipartFile}이 아니라 이 형인 것이 무저장 규칙의 자리다: 어댑터가 파일 핸들을 쥐지
     * 않으므로 「어디에 저장할지」를 정할 수 있는 코드 자체가 없다.
     *
     * @param mediaType {@code image/jpeg} | {@code image/png} | {@code image/webp}
     */
    public record ImagePart(String mediaType, byte[] bytes) {
    }

    /**
     * 전사 구조화 출력 스키마.
     *
     * @param text       읽어 낸 글(못 읽은 글자는 {@code [?]})
     * @param unreadable 공부 메모가 아니거나 글씨를 전혀 못 읽음 — 이때 {@code text}는 빈 값이다
     */
    public record Transcript(String text, boolean unreadable) {
    }

    /**
     * 구조화 출력 스키마 — SDK가 이 record에서 JSON 스키마를 만들고 응답을 이 타입으로 파싱한다.
     *
     * <p>Jackson 어노테이션을 붙이지 않는다(필드 의미는 시스템 프롬프트가 설명한다). 필드 이름이 곧
     * 스키마 키라 이름을 바꾸면 프롬프트의 설명과 어긋난다.
     *
     * @param summary   ① 사용자가 쓴 내용을 구조화한 정리
     * @param holes     ② 범위 안에서 빠졌거나 틀린 항목
     * @param questions ③ 내일 풀 복습문제
     */
    public record RecallAnalysis(String summary, List<String> holes, List<String> questions) {
    }
}
