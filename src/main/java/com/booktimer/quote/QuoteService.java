package com.booktimer.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Random;

/**
 * 작가 격언 제공 — 대시보드 인사말 자리에 랜덤으로 띄울 한 줄을 고른다.
 *
 * <p><b>소스(MVP)</b>는 앱 내장 큐레이션 목록({@code classpath:quotes.json})이다. 외부 명언 API는
 * 대시보드(핫패스)마다 네트워크 왕복·장애·영어 위주라 쓰지 않는다. 목록은 기동 시 한 번 읽어 불변 보관한다.
 *
 * <p><b>랜덤 이음새</b>: 선택은 비결정적이라 그대로 두면 테스트가 어렵다. 시각을 {@code Clock}으로 주입하듯
 * (N-010) {@link Random}을 주입받아, 테스트는 가짜/시드 Random으로 "어느 인덱스를 고르는가"를 결정적으로 검증한다.
 *
 * <p><b>왜 매 호출 새로 뽑나</b>: 대시보드 인사말은 htmx 라이브 영역 밖이라 전체 페이지 로드 때만 다시
 * 그려진다. 캐시 없이 매 {@link #random()} 호출마다 새로 뽑아야 "페이지를 띄울 때마다 바뀐다"가 성립한다.
 *
 * <p><b>미래 진화</b>(plan.md): ① 전역 랜덤 → ② 책장 작가 필터 → ③ 사용자 저장 문장. 대시보드가
 * "이 사용자용 격언 하나"만 묻도록 두면 소스 구현만 갈아끼워진다(컨트롤러·템플릿 무변경).
 */
@Service
public class QuoteService {

    private static final String QUOTES_RESOURCE = "quotes.json";

    private final List<Quote> quotes;
    private final Random random;

    /**
     * 운영 생성자 — classpath의 큐레이션 격언 목록을 적재하고 기본 {@link Random}을 쓴다.
     *
     * <p>이 SSR 앱엔 {@code ObjectMapper} 빈이 없으므로(T-022) 여기서 직접 만들어 읽는다.
     */
    public QuoteService() {
        this(loadFromClasspath(), new Random());
    }

    /**
     * 테스트용 생성자 — 격언 목록과 {@link Random}을 직접 주입한다(선택의 결정적 검증).
     *
     * @throws IllegalArgumentException 목록이 비어 있는 경우(랜덤 선택 불가)
     */
    QuoteService(List<Quote> quotes, Random random) {
        if (quotes == null || quotes.isEmpty()) {
            throw new IllegalArgumentException("quotes must not be empty");
        }
        this.quotes = List.copyOf(quotes);
        this.random = random;
    }

    /** 적재된 격언 전체(불변 뷰). */
    public List<Quote> all() {
        return quotes;
    }

    /** 격언 하나를 랜덤으로 고른다(매 호출 새로 — 캐시 없음). */
    public Quote random() {
        return quotes.get(random.nextInt(quotes.size()));
    }

    private static List<Quote> loadFromClasspath() {
        try (InputStream in = new ClassPathResource(QUOTES_RESOURCE).getInputStream()) {
            Quote[] loaded = new ObjectMapper().readValue(in, Quote[].class);
            return List.of(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load " + QUOTES_RESOURCE, e);
        }
    }
}
