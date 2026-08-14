package com.booktimer.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 구글 뉴스 RSS 어댑터 — 완독한 책의 관련 기사를 가져온다({@link BookNewsCollector}가 쓴다).
 *
 * <p><b>왜 구글 RSS인가</b>: 네이버 검색 API는 신규 권한 발급이 닫혔다(키를 발급해도 「검색」 scope가
 * 붙지 않아 {@code 401 Scope Status Invalid}). 구글 뉴스 RSS는 키가 없어도 되고 한국어 로케일
 * ({@code hl=ko&gl=KR&ceid=KR:ko})로 국내 기사를 준다.
 *
 * <p><b>킬스위치</b>: 공식 API가 아니라 형식이 예고 없이 바뀔 수 있어 {@code booktimer.news.enabled}
 * 하나로 통째로 끈다(기본 켜짐 — 키가 필요 없으니 배포 즉시 동작한다). 꺼지면 수집기는 no-op이고
 * 홈의 「책 뉴스」 탭도 사라진다.
 *
 * <p>HTTP 호출과 XML 매핑을 분리해, 매핑은 {@link #parse(String)} 정적 메서드로 네트워크 없이
 * 단위테스트한다. 외부 XML이라 파서는 XXE를 차단한 상태로만 만든다({@link #secureDocumentBuilder()}).
 */
@Component
public class GoogleNewsRssClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsRssClient.class);
    private static final String ENDPOINT = "https://news.google.com/rss/search";

    private final boolean enabled;
    private final RestClient restClient;

    public GoogleNewsRssClient(@Value("${booktimer.news.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        this.restClient = RestClient.create();
    }

    /** 킬스위치. 기본 true — 끄려면 SSM {@code BOOKTIMER_NEWS_ENABLED=false}. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 뉴스를 검색한다. 비활성이거나 쿼리가 비면 호출 없이 빈 목록.
     * 외부 장애는 빈 목록으로 격리한다(알라딘 패턴) — 한 책의 실패가 배치 전체를 깨지 않는다.
     */
    public List<NewsArticle> search(String query) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String body = restClient.get().uri(searchUrl(query)).retrieve().body(String.class);
            return parse(body);
        } catch (Exception e) {
            log.warn("구글 뉴스 RSS 검색 실패 — query='{}': {}", query, e.toString());
            return List.of();
        }
    }

    /**
     * 검색 쿼리를 만든다 — <b>정규화한 제목 + 저자</b>.
     *
     * <p>제목만 던지면 오탐이 급증한다(실측 2026-08-14: 『데미안』 최종 채택 6건 → 제목+저자 28건).
     * 따옴표 구절 검색은 쓰지 않는다 — 구글은 구절을 엄격히 적용해 재현율만 깎였다.
     * 정밀도의 2차 방어(사후 필터)는 {@link BookNewsMatcher}가 한다.
     *
     * <p>알라딘 원문("코스모스 - 특별판" / "칼 세이건 (지은이), 홍승수 (옮긴이)")을 그대로 던지면 검색
     * 결과부터 나빠지므로 {@link BookNewsMatcher#normalizeTitle}·{@link BookNewsMatcher#normalizeAuthor}를
     * 거친다 — 사후 필터와 <b>같은 값</b>을 쓰는 게 핵심이다(질의와 판정이 어긋나면 또 0건이 된다).
     */
    static String buildQuery(String title, String author) {
        return BookNewsMatcher.normalizeTitle(title) + " " + BookNewsMatcher.normalizeAuthor(author);
    }

    /** 검색 URL — 한글 질의를 퍼센트 인코딩하고 한국어 로케일 파라미터를 붙인다. */
    static URI searchUrl(String query) {
        return UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("q", query)
                .queryParam("hl", "ko")
                .queryParam("gl", "KR")
                .queryParam("ceid", "KR:ko")
                .build()
                .encode()
                .toUri();
    }

    /**
     * RSS(XML)를 기사 목록으로 매핑한다. 네트워크 없이 테스트 가능.
     *
     * <p>{@code <description>}은 구글 리다이렉트 링크가 든 {@code <a>} 태그뿐이라 매칭에 쓸 근거가 없어
     * 아예 담지 않는다(실측). {@code <source>}가 출처명을 구조화해 주므로 제목 접미사를 파싱할 필요가
     * 없고, 오히려 제목 끝의 중복 접미사 {@code " - 출처"}를 잘라낸다({@link #stripSourceSuffix}).
     * {@code <link>}는 구글 리다이렉트 주소이지 원문 URL이 아니다 — 그대로 저장·노출한다.
     *
     * <p>제목·링크가 없는 item은 건너뛰고, {@code pubDate}(RFC-822)를 못 읽으면 그 기사만
     * {@code publishedAt=null}로 두고 줄은 살린다. 깨진 XML은 빈 목록 — 외부 장애 격리.
     */
    static List<NewsArticle> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        List<NewsArticle> results = new ArrayList<>();
        try {
            NodeList items = secureDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)))
                    .getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String link = text(item, "link");
                String source = BookNewsMatcher.clean(text(item, "source"));
                String title = stripSourceSuffix(BookNewsMatcher.clean(text(item, "title")), source);
                if (isBlank(title) || isBlank(link)) {
                    continue;
                }
                results.add(new NewsArticle(title, link.strip(),
                        parsePubDate(text(item, "pubDate")), source));
            }
        } catch (Exception e) {
            log.warn("구글 뉴스 RSS 파싱 실패: {}", e.toString());
            return List.of();
        }
        return results;
    }

    /**
     * 기사 제목 끝의 {@code " - 출처"}를 잘라낸다 — 출처는 별도 필드로 오므로 중복이다.
     * 접미사가 없거나 출처를 모르면 제목을 그대로 둔다. 제목 <b>안</b>의 하이픈은 건드리지 않는다
     * (예 "미움받을 용기 - 기시미 이치로, 고가 후미타케 - 브런치" → 앞의 하이픈은 살린다).
     */
    static String stripSourceSuffix(String title, String source) {
        if (title == null || source == null || source.isBlank()) {
            return title;
        }
        String suffix = " - " + source.strip();
        return title.endsWith(suffix)
                ? title.substring(0, title.length() - suffix.length()).strip()
                : title;
    }

    /**
     * XXE를 차단한 DOM 파서. 외부에서 받은 XML을 파싱하므로 <b>DTD 자체를 거부</b>하고
     * (disallow-doctype-decl) 외부 엔티티·외부 DTD 로딩도 모두 끈다 — 공격 XML은 파싱 예외로 떨어져
     * {@link #parse}가 빈 목록으로 격리한다.
     *
     * <p>기본 ErrorHandler는 깨진 XML마다 stderr에 "[Fatal Error]"를 찍어 빌드 로그를 오염시킨다.
     * 어차피 예외로 잡아 로그를 남기므로 여기선 그대로 던지기만 한다.
     */
    private static DocumentBuilder secureDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                // 무시 — 경고로 기사를 버릴 이유는 없다.
            }

            @Override
            public void error(SAXParseException e) throws SAXException {
                throw e;
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                throw e;
            }
        });
        return builder;
    }

    /** RFC-822 발행 시각(예 "Thu, 02 Nov 2023 07:00:00 GMT") → Instant. 못 읽으면 null. */
    private static Instant parsePubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(raw.strip(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /** item의 자식 엘리먼트 텍스트. 없으면 null. */
    private static String text(Element item, String tagName) {
        NodeList nodes = item.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 구글 뉴스가 준 기사 한 건.
     *
     * @param link   구글 리다이렉트 주소(원문 URL이 아니다 — 클릭하면 원문으로 넘어간다)
     * @param source 매체명({@code <source>} 엘리먼트). 없으면 null
     */
    public record NewsArticle(String title, String link, Instant publishedAt, String source) {
    }
}
