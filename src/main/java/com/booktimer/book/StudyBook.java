package com.booktimer.book;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.session.ReadingSessionService;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 공부 서재에 등록된 책. User와 N:1.
 *
 * <p>독서 책장({@link Book})과 <b>다른 테이블</b>이다(V81 주석) — 공부 책의 분류는 상태
 * (읽고싶음/읽는중/완독)가 아니라 <b>회독 수</b>이고, 독서 도메인 부속(피드 스탬프·공개 범위·제휴 클릭
 * 카운터·책BTI 카탈로그 메타)은 공부에 소비처가 없다. 두 서재가 섞이지 않는 것이 요구 그 자체라,
 * 격리를 필터가 아니라 테이블 경계로 얻는다.
 *
 * <p>{@code purchaseLink}만 남긴 이유: 검색 응답에 함께 실려 오고 수험서는 구매 전환이 실제로 기대된다.
 */
@Entity
@Table(name = "study_book")
public class StudyBook extends BaseTimeEntity {

    /** 제목 길이 상한 = 컬럼 길이. 검사가 없으면 초과분이 DB에서 터져 <b>500</b>이 된다(문 앞 400으로 앞당긴다). */
    public static final int MAX_TITLE_LENGTH = 300;

    /** 바로가기 링크 길이 상한 = 컬럼 길이(강의 URL은 쿼리가 길다). */
    public static final int MAX_LINK_URL_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = 200)
    private String author;

    /** ISBN-13. 같은 책 재추가를 막는 동일성 키({@link Isbn#normalize}로 표기를 모은다). */
    @Column(length = 20)
    private String isbn13;

    @Column(length = 500)
    private String coverUrl;

    @Column(length = 200)
    private String publisher;

    /** 구매 링크(제휴 태그 포함 가능). 검색 결과에서 받는다. */
    @Column(length = 1000)
    private String purchaseLink;

    /**
     * 바로가기 링크(인강 페이지 등) — 사용자가 직접 적는다. <b>식별자가 아니다</b>: 어디서도 키로 안 쓰고,
     * 죽어도 제목·시간·필기·잔디는 무관하다(강의는 해마다 개편되고 수강은 만료된다).
     *
     * <p>{@code purchaseLink}와 <b>따로 두는 것이 요구 그 자체</b>다 — 그쪽은 제휴 구매 링크라는 의미가
     * 박혀 있어(미니앱이 「알라딘에서 구매」 + 수수료 고지를 그린다) 인강 URL을 실으면 표기가 거짓이 된다.
     *
     * <p>http/https만 들어온다({@link #normalizeLinkUrl}) — 웹이 이 값을 {@code :href}로 그대로 열고
     * Vue도 {@code <input type="url">}도 {@code javascript:}를 걸러 주지 않으니 여기가 유일한 벨트다.
     */
    @Column(name = "link_url", length = MAX_LINK_URL_LENGTH)
    private String linkUrl;

    /**
     * 회독 수 — 이 서재의 유일한 분류 축이다. 0은 「아직 한 번도 안 돌았다」는 정보라 표시 대상이다
     * (「없음」이 아니다). 음수는 {@link #changeReadCount(int)}가 막는다.
     */
    @Column(nullable = false)
    private int readCount = 0;

    /**
     * 회당 시간(초) — 이 책으로 한 번 앉을 때 공부할 시간. null = 안 정함(스톱워치). 정하면
     * {@value #MIN_SESSION_GOAL_SECONDS} 이상 측정 상한 이하다({@link #validateSessionGoal}).
     */
    @Column(name = "session_goal_seconds")
    private Integer sessionGoalSeconds;

    /**
     * 회당 시간 하한 — 10분. 0은 「해제」가 아니다(해제는 null). 2026-09-15 1분에서 올렸다 — 공부를 1분 단위로
     * 앉지 않고, 짧은 값은 달성 푸시(60초 폴링·측정 중인 세션만)가 닿자마자 정지에 원리상 안 가서 헷갈리게만 했다.
     * 올리기 전에 저장된 10분 미만 값은 그대로 읽힌다(검사는 쓰기에만 있다).
     */
    public static final int MIN_SESSION_GOAL_SECONDS = 600;

    /** 회당 시간 상한 — 측정 상한(6시간)과 같다. 그보다 긴 회당 시간은 한 측정으로 닿을 수 없다. */
    public static final int MAX_SESSION_GOAL_SECONDS =
            (int) ReadingSessionService.MAX_SESSION_DURATION.toSeconds();

    protected StudyBook() {
        // JPA
    }

    private StudyBook(User user, String title, String author, String isbn13,
                      String coverUrl, String publisher, String purchaseLink, String linkUrl) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.strip().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title too long: " + title.strip().length());
        }
        this.user = user;
        this.title = title.strip();
        this.author = author;
        // 적재 단일 통로 — 표기가 갈리면 멱등 가드가 같은 책을 다른 책으로 본다(빈 값→null).
        this.isbn13 = Isbn.normalize(isbn13);
        this.coverUrl = coverUrl;
        this.publisher = publisher;
        this.purchaseLink = purchaseLink;
        // isbn과 같은 규율 — 적재 단일 통로다(검사를 빠져나간 값이 DB에 남는 창을 만들지 않는다).
        this.linkUrl = normalizeLinkUrl(linkUrl);
    }

    /**
     * 공부 서재에 새 책을 등록한다 — 언제나 <b>0독</b>으로 시작한다(상태 선택이 없는 이유).
     *
     * @param user  소유자(필수)
     * @param title 제목(필수, 공백 불가)
     */
    public static StudyBook register(User user, String title, String author, String isbn13,
                                     String coverUrl, String publisher, String purchaseLink) {
        return register(user, title, author, isbn13, coverUrl, publisher, purchaseLink, null);
    }

    /**
     * 바로가기 링크까지 실어 등록한다 — 인강처럼 ISBN·표지가 없는 항목이 이 문으로 들어온다.
     * 링크 없는 호출은 위 7인자 판을 그대로 쓴다(기존 호출부 무변경).
     *
     * @param linkUrl 강의 페이지 등(선택). 빈 값은 null, http/https가 아니면 IAE({@link #normalizeLinkUrl})
     */
    public static StudyBook register(User user, String title, String author, String isbn13,
                                     String coverUrl, String publisher, String purchaseLink, String linkUrl) {
        return new StudyBook(user, title, author, isbn13, coverUrl, publisher, purchaseLink, linkUrl);
    }

    /**
     * 회독 수를 <b>절대값으로</b> 설정한다(델타가 아니다) — 멱등이라 연타·재시도에 안전하고,
     * 미래의 직접 편집(숫자 입력)도 같은 문을 쓴다.
     *
     * @throws IllegalArgumentException 음수인 경우("−1독"은 없다)
     */
    public void changeReadCount(int readCount) {
        if (readCount < 0) {
            throw new IllegalArgumentException("readCount must not be negative: " + readCount);
        }
        this.readCount = readCount;
    }

    /**
     * 회당 시간을 정하거나(초) 해제한다(null).
     *
     * @throws IllegalArgumentException 범위 밖인 경우({@link #validateSessionGoal})
     */
    public void changeSessionGoal(Integer seconds) {
        validateSessionGoal(seconds);
        this.sessionGoalSeconds = seconds;
    }

    /**
     * 바로가기 링크를 바꾸거나 해제한다(빈 값 = 해제) — 링크는 1년 안에 죽는다는 전제의 직접 귀결이다.
     * 「지우고 다시 담기」로 대신할 수 없다: 회독 수를 잃고, 필기가 붙은 책은 삭제 자체가 409다.
     *
     * @throws IllegalArgumentException http/https가 아니거나 너무 긴 경우({@link #normalizeLinkUrl})
     */
    public void changeLinkUrl(String raw) {
        this.linkUrl = normalizeLinkUrl(raw);
    }

    /**
     * 링크 값 규칙 — blank는 null, 아니면 strip 후 {@value #MAX_LINK_URL_LENGTH}자 이하이고 파싱 가능한
     * <b>http/https</b> 주소이며 host가 있어야 한다.
     *
     * <p>static인 이유는 {@link #validateSessionGoal}과 같다: 문(컨트롤러)이 <b>소유권 조회보다 먼저</b>
     * 부를 수 있어야 남의 책 id로 400/404를 갈라 존재를 캐낼 창이 안 열린다. 메시지는 400 본문으로
     * 화면에 그대로 뜨므로 한국어 완성문이다.
     */
    public static String normalizeLinkUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String link = raw.strip();
        if (link.length() > MAX_LINK_URL_LENGTH) {
            throw new IllegalArgumentException("링크는 " + MAX_LINK_URL_LENGTH + "자 이내로 적어 주세요");
        }
        URI uri;
        try {
            uri = new URI(link);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(LINK_SCHEME_MESSAGE);
        }
        String scheme = uri.getScheme();
        // host까지 보는 이유: "https://"는 스킴만 맞고 열 곳이 없다(빈 탭이 열린다).
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(LINK_SCHEME_MESSAGE);
        }
        return link;
    }

    private static final String LINK_SCHEME_MESSAGE = "링크는 http:// 또는 https://로 시작하는 주소여야 해요";

    /**
     * 회당 시간 값 규칙 — null 허용, 아니면 600 ≤ v ≤ 21600. 문(컨트롤러)이 <b>소유권 조회보다 먼저</b>
     * 부를 수 있게 static으로 뺐다(남의 책 id로 400/404를 갈라 존재를 캐낼 창을 막는다).
     * 메시지는 400 본문으로 화면에 뜨므로 한국어 완성문이다.
     */
    public static void validateSessionGoal(Integer seconds) {
        if (seconds != null && (seconds < MIN_SESSION_GOAL_SECONDS || seconds > MAX_SESSION_GOAL_SECONDS)) {
            throw new IllegalArgumentException("회당 시간은 10분에서 6시간 사이로 정해 주세요");
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getPurchaseLink() {
        return purchaseLink;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public int getReadCount() {
        return readCount;
    }

    public Integer getSessionGoalSeconds() {
        return sessionGoalSeconds;
    }
}
