package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오(다음) 책 검색 어댑터 — 실제 아웃바운드 요청(URL · 헤더)과 응답 매핑, 그리고 <b>장애를 0건과 가르는 규칙</b>(T-278).
 *
 * <p>픽스처는 카카오 개발 가이드의 응답 예시(『미움받을 용기』)를 줄인 것이다.
 */
@ExtendWith(OutputCaptureExtension.class)
class KakaoBookSearchClientTest {

    private static final String KEY = "TESTKEY123";

    /** 카카오 문서 예시를 줄인 한 건 — isbn은 「10 13」 꼴, datetime은 ISO 8601. */
    private static final String DOC_MIUM = """
            {"title":"미움받을 용기","contents":"…","url":"https://search.daum.net/x",
             "isbn":"8996991341 9788996991342","datetime":"2014-11-17T00:00:00.000+09:00",
             "authors":["기시미 이치로","고가 후미타케"],"publisher":"인플루엔셜","translators":["전경아"],
             "price":14900,"sale_price":13410,
             "thumbnail":"https://search1.kakaocdn.net/thumb/R120x174.q85/?fname=http%3A%2F%2Ft1.daumcdn.net%2Fx",
             "status":"정상판매"}""";

    private static String body(String... docs) {
        return "{\"meta\":{\"is_end\":true,\"pageable_count\":" + docs.length + ",\"total_count\":" + docs.length
                + "},\"documents\":[" + String.join(",", docs) + "]}";
    }

    private static String doc(String title, String isbn, String thumbnail, String datetime) {
        return "{\"title\":\"" + title + "\",\"isbn\":\"" + isbn + "\",\"thumbnail\":\"" + thumbnail
                + "\",\"datetime\":\"" + datetime + "\",\"authors\":[\"저자\"],\"translators\":[],\"publisher\":\"출판사\"}";
    }

    private record Fixture(KakaoBookSearchClient client, MockRestServiceServer server) {}

    private static Fixture fixture(String key) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new KakaoBookSearchClient(key, builder.build()), server);
    }

    private static Fixture fixture() {
        return fixture(KEY);
    }

    // ── REQ-380 · 요청 ────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-380 · 키가 없거나 not-configured면 비활성이다")
    void disabledWithoutKey() {
        assertThat(fixture("not-configured").client().isEnabled()).isFalse();
        assertThat(fixture("  ").client().isEnabled()).isFalse();
        assertThat(fixture(null).client().isEnabled()).isFalse();
        assertThat(fixture(KEY).client().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("REQ-380 · 제목 검색은 target=title · size=40 · page로 간다")
    void titleSearch_request() {
        Fixture f = fixture();
        f.server().expect(requestTo(allOf(
                        containsString("https://dapi.kakao.com/v3/search/book?"),
                        containsString("target=title"), containsString("size=40"), containsString("page=2"))))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

        f.client().search("데미안", BookSearchType.TITLE, 2);

        f.server().verify();
    }

    @Test
    @DisplayName("REQ-380 · 저자는 person, 출판사는 publisher로 간다")
    void authorAndPublisher_targets() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=person")))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
        f.server().expect(requestTo(containsString("target=publisher")))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

        f.client().search("헤세", BookSearchType.AUTHOR, 1);
        f.client().search("민음사", BookSearchType.PUBLISHER, 1);

        f.server().verify();
    }

    @Test
    @DisplayName("REQ-380 · page는 1~50으로 자른다")
    void pageIsClamped() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("page=1")))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
        f.server().expect(requestTo(containsString("page=50")))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

        assertThat(f.client().search("데미안", BookSearchType.TITLE, 0).page()).isEqualTo(1);
        assertThat(f.client().search("데미안", BookSearchType.TITLE, 99).page()).isEqualTo(50);

        f.server().verify();
    }

    @Test
    @DisplayName("REQ-380 · 검색어는 한 번만 인코딩된다")
    void queryEncodedOnce() {
        Fixture f = fixture();
        f.server().expect(requestTo(allOf(
                        containsString("query=a%26b%3Dc%23d"), not(containsString("%2526")))))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

        f.client().search("a&b=c#d", BookSearchType.TITLE, 1);

        f.server().verify();
    }

    @Test
    @DisplayName("REQ-380 · 키는 Authorization 헤더로만 간다")
    void keyOnlyInHeader() {
        Fixture f = fixture();
        f.server().expect(requestTo(not(containsString(KEY))))
                .andExpect(header("Authorization", "KakaoAK " + KEY))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

        f.client().search("데미안", BookSearchType.TITLE, 1);

        f.server().verify();
    }

    // ── REQ-381 · 매핑 ────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-381 · 문서를 검색 결과로 옮긴다")
    void mapsDocument() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=title")))
                .andRespond(withSuccess(body(DOC_MIUM), MediaType.APPLICATION_JSON));

        BookSearchPage page = f.client().search("미움받을 용기", BookSearchType.TITLE, 1);

        assertThat(page.totalResults()).isEqualTo(1);
        assertThat(page.results()).containsExactly(new BookSearchResult(
                "미움받을 용기",
                "기시미 이치로, 고가 후미타케, 전경아 (옮긴이)",
                "9788996991342",
                "https://search1.kakaocdn.net/thumb/R120x174.q85/?fname=http%3A%2F%2Ft1.daumcdn.net%2Fx",
                "인플루엔셜",
                null,
                null,
                "2014-11-17"));
    }

    @Test
    @DisplayName("REQ-381 · ISBN 토큰을 고른다")
    void picksIsbnToken() {
        assertThat(KakaoBookSearchClient.isbn13Of("8996991341 9788996991342")).isEqualTo("9788996991342");
        assertThat(KakaoBookSearchClient.isbn13Of(" 9788996991342")).isEqualTo("9788996991342");
        assertThat(KakaoBookSearchClient.isbn13Of("8996991341 ")).isEqualTo("9788996991342");
        assertThat(KakaoBookSearchClient.isbn13Of("080442957X")).isEqualTo("9780804429573");
        assertThat(KakaoBookSearchClient.isbn13Of("978-89-96991-34-2")).isEqualTo("9788996991342");
        assertThat(KakaoBookSearchClient.isbn13Of("")).isNull();
        assertThat(KakaoBookSearchClient.isbn13Of(null)).isNull();
    }

    @Test
    @DisplayName("REQ-381 · 빈 표지 · 빈 날짜는 null이다")
    void blankCoverAndDate_areNull() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=title")))
                .andRespond(withSuccess(body(doc("표지 없음", "9788996991342", "", "")),
                        MediaType.APPLICATION_JSON));

        BookSearchResult r = f.client().search("표지", BookSearchType.TITLE, 1).results().get(0);

        assertThat(r.coverUrl()).isNull();
        assertThat(r.pubDate()).isNull();
        assertThat(KakaoBookSearchClient.pubDateOf("2014년")).isNull();
        assertThat(KakaoBookSearchClient.pubDateOf(null)).isNull();
    }

    @Test
    @DisplayName("REQ-381 · 저자 · 옮긴이가 둘 다 비면 author는 null이다")
    void noWriters_authorNull() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=title")))
                .andRespond(withSuccess(body("{\"title\":\"무명\",\"isbn\":\"9788996991342\",\"authors\":[],\"translators\":[]}"),
                        MediaType.APPLICATION_JSON));

        assertThat(f.client().search("무명", BookSearchType.TITLE, 1).results().get(0).author()).isNull();
    }

    @Test
    @DisplayName("REQ-381 · 같은 ISBN이 두 번 오면 앞 것만 남긴다")
    void duplicateIsbn_keepsFirst() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=title")))
                .andRespond(withSuccess(body(
                        doc("앞", "9788996991342", "", ""),
                        doc("뒤", "8996991341", "", ""),
                        doc("isbn 없음 1", "", "", ""),
                        doc("isbn 없음 2", "", "", "")), MediaType.APPLICATION_JSON));

        List<BookSearchResult> rs = f.client().search("x", BookSearchType.TITLE, 1).results();

        assertThat(rs).extracting(BookSearchResult::title).containsExactly("앞", "isbn 없음 1", "isbn 없음 2");
    }

    // ── REQ-382 · 바코드 ──────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-382 · ISBN 조회는 target=isbn으로 가고 ISBN이 같은 문서 하나를 돌려준다")
    void lookup_returnsMatchingIsbn() {
        Fixture f = fixture();
        f.server().expect(requestTo(allOf(containsString("target=isbn"), containsString("query=9788996991342"))))
                .andExpect(header("Authorization", "KakaoAK " + KEY))
                .andRespond(withSuccess(body(doc("다른 책", "9791111111111", "", ""), DOC_MIUM),
                        MediaType.APPLICATION_JSON));

        Optional<BookSearchResult> r = f.client().lookupByIsbn("9788996991342");

        assertThat(r).map(BookSearchResult::title).contains("미움받을 용기");
        f.server().verify();
    }

    @Test
    @DisplayName("REQ-382 · ISBN이 다른 책뿐이면 빈 결과다")
    void lookup_otherIsbnOnly_isEmpty() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=isbn")))
                .andRespond(withSuccess(body(doc("다른 책", "9791111111111", "", "")), MediaType.APPLICATION_JSON));

        assertThat(f.client().lookupByIsbn("9788996991342")).isEmpty();
    }

    // ── REQ-383 · 장애는 0건이 아니다 ───────────────────────────────────────

    @Test
    @DisplayName("REQ-383 · 200인데 documents가 없으면 0건이 아니라 장애다")
    void okWithoutDocuments_isUnavailable() {
        for (String bad : List.of(
                "{\"errorCode\":999,\"errorMessage\":\"www.aladin.co.kr 도메인을 통한 OpenAPI서비스는 종료되었습니다.\"}",
                "<!DOCTYPE html><html><body>점검 중</body></html>",
                "")) {
            Fixture f = fixture();
            f.server().expect(requestTo(containsString("target=title")))
                    .andRespond(withSuccess(bad, MediaType.APPLICATION_JSON));
            f.server().expect(requestTo(containsString("target=isbn")))
                    .andRespond(withSuccess(bad, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> f.client().search("데미안", BookSearchType.TITLE, 1))
                    .as("본문: %s", bad).isInstanceOf(BookSearchUnavailableException.class);
            assertThatThrownBy(() -> f.client().lookupByIsbn("9788996991342"))
                    .as("본문: %s", bad).isInstanceOf(BookSearchUnavailableException.class);
        }
    }

    @Test
    @DisplayName("REQ-383 · 401 · 429 · 500이면 장애다")
    void httpErrors_areUnavailable() {
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR)) {
            Fixture f = fixture();
            f.server().expect(requestTo(containsString("target=title")))
                    .andRespond(withStatus(status).body("{\"errorType\":\"x\",\"message\":\"y\"}")
                            .contentType(MediaType.APPLICATION_JSON));
            f.server().expect(requestTo(containsString("target=isbn")))
                    .andRespond(withStatus(status));

            assertThatThrownBy(() -> f.client().search("데미안", BookSearchType.TITLE, 1))
                    .as("상태: %s", status).isInstanceOf(BookSearchUnavailableException.class);
            assertThatThrownBy(() -> f.client().lookupByIsbn("9788996991342"))
                    .as("상태: %s", status).isInstanceOf(BookSearchUnavailableException.class);
        }
    }

    @Test
    @DisplayName("REQ-383 · 읽기 시간 초과는 장애다")
    void timeout_isUnavailable() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=title")))
                .andRespond(req -> { throw new SocketTimeoutException("Read timed out"); });

        assertThatThrownBy(() -> f.client().search("데미안", BookSearchType.TITLE, 1))
                .isInstanceOf(BookSearchUnavailableException.class);
    }

    /**
     * 카카오는 잘못된 키에 그 키를 본문으로 되돌려준다(「wrong appKey(&lt;보낸 키&gt;) format」). SSM 값에 군더더기가 섞이면
     * 진짜 키가 실린다 — 그 본문은 로그에도, 예외 메시지 · cause 사슬에도 남지 않아야 한다.
     */
    @Test
    @DisplayName("REQ-380 · HTTP 오류 본문에 되돌아온 키는 로그 · 예외 어디에도 남지 않는다")
    void errorBodyEchoingKey_isNotLoggedOrChained(CapturedOutput output) {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=title")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"errorType\":\"AccessDeniedError\",\"message\":\"wrong appKey(" + KEY + ") format\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> f.client().search("데미안", BookSearchType.TITLE, 1));

        assertThat(thrown).isInstanceOf(BookSearchUnavailableException.class);
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            assertThat(String.valueOf(t.getMessage())).as("예외 사슬: %s", t.getClass()).doesNotContain(KEY);
        }
        assertThat(output.getAll()).contains("http=401").doesNotContain(KEY);
    }

    /** 응답하지 않는 서버에 붙으면 읽기 상한(5초) 안에 장애로 끝난다 — 상한이 없으면 앱의 「찾는 중」이 무한히 돈다(E21). */
    @Test
    @DisplayName("REQ-380 · 응답 없는 서버는 읽기 상한 안에 장애로 끝난다")
    void silentServer_timesOutAsUnavailable() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            // accept하지 않는다 — 연결은 백로그에서 맺어지지만 응답이 영영 오지 않는다
            URI local = URI.create("http://127.0.0.1:" + silent.getLocalPort() + "/v3/search/book");
            RestClient toLocal = KakaoBookSearchClient.defaultRestClient().mutate()
                    .requestInterceptor((req, body, exec) -> exec.execute(new HttpRequestWrapper(req) {
                        @Override
                        public URI getURI() {
                            return local.resolve("?" + req.getURI().getRawQuery());
                        }
                    }, body))
                    .build();
            KakaoBookSearchClient client = new KakaoBookSearchClient(KEY, toLocal);

            assertTimeoutPreemptively(Duration.ofSeconds(7), () ->
                    assertThatThrownBy(() -> client.search("데미안", BookSearchType.TITLE, 1))
                            .isInstanceOf(BookSearchUnavailableException.class));
        }
    }

    @Test
    @DisplayName("REQ-383 · documents가 빈 배열이면 0건이다(장애 아님)")
    void emptyDocuments_isZeroResults() {
        Fixture f = fixture();
        f.server().expect(requestTo(containsString("target=title")))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
        f.server().expect(requestTo(containsString("target=isbn")))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));

        assertThat(f.client().search("없는책", BookSearchType.TITLE, 1).results()).isEmpty();
        assertThat(f.client().lookupByIsbn("9788996991342")).isEmpty();
        f.server().verify();
    }

    @Test
    @DisplayName("REQ-383 · 키가 없으면 검색 · ISBN 조회가 장애로 던진다(외부 호출 없음)")
    void noKey_throwsWithoutCall() {
        Fixture f = fixture("not-configured");

        assertThatThrownBy(() -> f.client().search("데미안", BookSearchType.TITLE, 1))
                .isInstanceOf(BookSearchUnavailableException.class);
        assertThatThrownBy(() -> f.client().lookupByIsbn("9788996991342"))
                .isInstanceOf(BookSearchUnavailableException.class);
        f.server().verify(); // 기대 요청 0개 — 하나라도 나갔으면 여기서 실패
    }

    @Test
    @DisplayName("REQ-383 · 검색어가 비면 호출 없이 0건이다")
    void blankQuery_noCall() {
        Fixture f = fixture();

        assertThat(f.client().search("  ", BookSearchType.TITLE, 1).results()).isEmpty();
        assertThat(f.client().search(null, BookSearchType.TITLE, 1).results()).isEmpty();
        assertThat(f.client().lookupByIsbn(" ")).isEmpty();
        f.server().verify();
    }

    // ── REQ-385 · 베스트셀러 ───────────────────────────────────────────────

    @Test
    @DisplayName("REQ-385 · 베스트셀러는 외부 호출 없이 빈 목록이다")
    void bestsellers_emptyWithoutCall() {
        Fixture f = fixture();

        assertThat(f.client().bestsellers()).isEmpty();
        f.server().verify();
    }
}
