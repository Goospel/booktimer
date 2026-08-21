package com.booktimer.book;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 사용자 책장에 등록된 책. User와 N:1.
 *
 * <p>제목 외 메타데이터(저자·ISBN·표지·출판사·구매링크)는 도서 검색 API(알라딘)에서 받아 채우거나,
 * 검색이 불가할 때 수동 입력한다. 구매링크는 추후 제휴(어필리에이트) 수익의 토대가 된다.
 * 책별 누적 독서 시간(세션 연결)은 다음 증분의 확장점이다.
 */
@Entity
@Table(name = "book")
public class Book extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 200)
    private String author;

    /** ISBN-13. 검색으로 등록 시 채워지고, 같은 책 식별/제휴에 쓰인다. */
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
     * 장르/카테고리(알라딘 categoryName, 예 "국내도서&gt;소설/시/희곡&gt;한국소설").
     * 책BTI(독서 성향 분석)의 장르 편향 집계 입력. 검색 등록 시 채워지고 수동 등록은 null(백필 대상).
     */
    @Column(length = 300)
    private String category;

    /**
     * 출간일(알라딘 pubDate, 예 "2020-03-15"). 책BTI의 신/구 분포 집계 입력.
     * 연도 추출·정규화는 집계 단계(Phase 2)의 몫이라 원문 문자열로 보존한다(적재 견고성).
     */
    @Column(length = 20)
    private String pubDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status;

    /**
     * 완독 시각 — 책방 "완독 시간 순" 정렬의 데이터 소스. 불변식: FINISHED ⇒ finishedAt 존재
     * (완독 이탈 시 클리어). 기록·클리어는 {@link #changeStatus(BookStatus, Instant)}가 담당하고,
     * 시각(Clock)은 서비스가 넘긴다(엔티티는 now를 모름 — 하우스 스타일). 완독 상태로 <b>등록</b>되는
     * 책도 서비스({@code BookService})가 등록 직후 changeStatus로 스탬프한다. V58 이전의 기존 완독
     * 책은 updated_at으로 백필된 근사치다.
     */
    @Column
    private Instant finishedAt;

    /**
     * 읽기 시작 시각 — 홈 소식 피드 "읽기 시작했어요" 이벤트의 데이터 소스.
     * {@link #finishedAt}의 미러이되 <b>한 곳이 다르다</b>: 완독으로 넘어가도 지우지 않는다 —
     * 시작·완독 두 이벤트가 시간순으로 공존하는 게 피드에선 맞다. <b>책당 영구 1회</b>라 재독(완독→읽는중)에도
     * 재스탬프하지 않는다 — 밀면 옛 책이 피드 맨 위에 "방금 시작"처럼 재부상한다.
     * 기록은 {@link #changeStatus(BookStatus, Instant)}·{@link #startReading(Instant)}가 담당하고
     * 시각(Clock)은 서비스가 넘긴다. V64 이전의 기존 책은 백필하지 않아 null이다(피드 창 14일 밖이라 무해).
     */
    @Column
    private Instant startedReadingAt;

    /**
     * 완독 축하 푸시 멱등 마커 — <b>책당 영구 1회</b>. {@link #finishedAt}과 달리 완독 이탈에도
     * 지우지 않는다: 지우면 완독↔읽는중 토글마다 축하가 다시 나가 알람 테러가 된다.
     * 기록은 {@link #markFinishCelebrated(Instant)}가 담당하고, 시각(Clock)은 서비스가 넘긴다.
     */
    @Column(name = "finish_celebrated_at")
    private Instant finishCelebratedAt;

    /**
     * 첫 완독 시각 — 홈 소식 피드 "완독했어요" 이벤트의 시각(책당 영구 1회).
     * {@link #finishedAt}과 달리 완독 이탈에도 지우지 않고, 재완독에도 재스탬프하지 않는다 —
     * 지우거나 밀면 옛 완독이 피드 맨 위에 "방금"처럼 재부상한다({@link #finishCelebratedAt}과 같은 이유).
     * 현재-완독 판정은 여전히 {@link #finishedAt}/{@link #status} 몫이다
     * (완독 취소 시 피드 제거는 {@code BookRepository.feedFinished}의 status 조건이 담당).
     */
    @Column(name = "first_finished_at")
    private Instant firstFinishedAt;

    /**
     * 알라딘 "구매"(제휴 링크) 클릭 누적 수. 어떤 책이 구매 의향을 내는지 보는 제휴 수익 데이터.
     * (이름은 하위호환으로 유지 — 쿠팡 도입 후 의미상 "알라딘 클릭"으로 굳었다. 쿠팡은 {@link #coupangClickCount}.)
     */
    @Column(nullable = false)
    private long clickCount = 0L;

    /** 쿠팡 파트너스 "구매" 클릭 누적 수. 알라딘({@link #clickCount})과 분리 집계 — 파트너별 성과 비교. */
    @Column(nullable = false)
    private long coupangClickCount = 0L;

    /** Yes24 제휴 "구매" 클릭 누적 수. 알라딘({@link #clickCount})·쿠팡({@link #coupangClickCount})과 분리 집계 — 파트너별 성과 비교. */
    @Column(nullable = false)
    private long yes24ClickCount = 0L;

    /** 교보문고 제휴 "구매" 클릭 누적 수. 알라딘·쿠팡·Yes24와 분리 집계 — 파트너별 성과 비교. */
    @Column(nullable = false)
    private long kyoboClickCount = 0L;

    /**
     * 공개 범위(SNS). 기본 비공개 — 공개는 사용자가 책마다 명시적으로 켠다(opt-in).
     * 기존 책은 마이그레이션(V8)에서 PRIVATE로 백필된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookVisibility visibility = BookVisibility.PRIVATE;

    protected Book() {
        // JPA
    }

    private Book(User user, String title, String author, String isbn13,
                 String coverUrl, String publisher, String purchaseLink,
                 String category, String pubDate, BookStatus status) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.user = user;
        this.title = title.strip();
        this.author = author;
        // 적재 단일 통로 — 검색/수동/미래 경로 무관하게 동일성 키를 한 표기로 모은다(빈 값→null).
        this.isbn13 = Isbn.normalize(isbn13);
        this.coverUrl = coverUrl;
        this.publisher = publisher;
        this.purchaseLink = purchaseLink;
        // 책BTI 카탈로그 메타 — "메타 없음"을 null 한 표기로 통일(집계·백필 동일 취급).
        this.category = blankToNull(category);
        this.pubDate = blankToNull(pubDate);
        this.status = status;
    }

    /** 빈/공백 문자열은 null로 정규화 — 카탈로그 메타의 "없음"을 한 표기로 모은다. */
    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * 책장에 새 책을 등록한다.
     *
     * @param user   소유자(필수)
     * @param title  제목(필수, 공백 불가)
     * @param status 초기 상태(필수)
     */
    public static Book register(User user, String title, String author, String isbn13,
                                String coverUrl, String publisher, String purchaseLink, BookStatus status) {
        return new Book(user, title, author, isbn13, coverUrl, publisher, purchaseLink, null, null, status);
    }

    /**
     * 카탈로그 메타(장르·출간일)까지 받아 책장에 등록한다 — 검색(알라딘) 경로 전용.
     * 책BTI(독서 성향 분석)의 입력이 되는 장르·출간일을 등록 시점에 함께 적재한다.
     *
     * @param category 장르/카테고리(알라딘 categoryName, 없으면 null)
     * @param pubDate  출간일(알라딘 pubDate, 없으면 null)
     */
    public static Book register(User user, String title, String author, String isbn13,
                                String coverUrl, String publisher, String purchaseLink,
                                String category, String pubDate, BookStatus status) {
        return new Book(user, title, author, isbn13, coverUrl, publisher, purchaseLink,
                category, pubDate, status);
    }

    /**
     * 읽기를 시작했음을 반영한다 — "읽고싶음"이면 "읽는중"으로 자동 전환한다.
     *
     * <p>멱등하다: 이미 "읽는중"이거나 "완독"인 책은 건드리지 않는다(완독을 읽는중으로 되돌리지 않음).
     * 타이머로 이 책을 처음 읽기 시작할 때 호출해, 사용자가 손으로 상태를 바꾸는 수고를 던다.
     *
     * @param now 현재 시각(필수) — 전환이 일어날 때만 {@link #startedReadingAt}에 스탬프한다
     * @return 실제로 전환이 일어났으면 true(=영속 저장이 필요), 그대로면 false
     */
    public boolean startReading(Instant now) {
        if (status == BookStatus.WANT_TO_READ) {
            changeStatus(BookStatus.READING, now);
            return true;
        }
        return false;
    }

    /** 제휴 "구매" 링크(알라딘) 클릭을 1회 집계한다(수익 데이터). */
    public void recordPurchaseClick() {
        this.clickCount++;
    }

    /** 쿠팡 파트너스 "구매" 클릭을 1회 집계한다(알라딘과 분리 집계). */
    public void recordCoupangClick() {
        this.coupangClickCount++;
    }

    /** Yes24 제휴 "구매" 클릭을 1회 집계한다(알라딘·쿠팡과 분리 집계). */
    public void recordYes24Click() {
        this.yes24ClickCount++;
    }

    /** 교보문고 제휴 "구매" 클릭을 1회 집계한다(알라딘·쿠팡·Yes24와 분리 집계). */
    public void recordKyoboClick() {
        this.kyoboClickCount++;
    }

    /**
     * 카탈로그 메타(장르·출간일)를 채운다 — 기존 책 백필(알라딘 ItemLookUp) 경로에서 쓴다.
     * register와 동일하게 빈/공백은 null로 정규화한다("없음"을 한 표기로). 덮어쓸지(이미 값 있는 책)는
     * 호출자(백필 서비스)가 대상 선별로 결정한다 — 서비스는 category가 null인 책만 고른다.
     */
    public void applyCatalogMetadata(String category, String pubDate) {
        this.category = blankToNull(category);
        this.pubDate = blankToNull(pubDate);
    }

    /**
     * 제휴 구매링크를 갱신한다 — 기존 책 purchaseLink 백필(알라딘 ItemLookUp, includeKey=1 재조회) 경로에서 쓴다.
     * TTBKey가 빠진 옛 링크를 ttbkey 실린 정식 링크로 교체한다. 대상 선별과 유효성(ttbkey 포함 여부) 판단은
     * 호출자(백필 서비스)가 하므로 여기선 단순 대입만 한다 — 무추적 링크로 덮어쓰지 않게 막는 건 호출자 책임.
     */
    public void updatePurchaseLink(String purchaseLink) {
        this.purchaseLink = purchaseLink;
    }

    /** 이 책을 공개한다 — 다른 사용자가 조회할 수 있게 된다(SNS). */
    public void makePublic() {
        this.visibility = BookVisibility.PUBLIC;
    }

    /** 이 책을 비공개로 되돌린다 — 나만 볼 수 있다. */
    public void makePrivate() {
        this.visibility = BookVisibility.PRIVATE;
    }

    /** 공개 범위를 직접 지정한다(공개/비공개). */
    public void changeVisibility(BookVisibility newVisibility) {
        if (newVisibility == null) {
            throw new IllegalArgumentException("visibility must not be null");
        }
        this.visibility = newVisibility;
    }

    /** 다른 사용자에게 보이는 공개 책인가. */
    public boolean isPublic() {
        return visibility == BookVisibility.PUBLIC;
    }

    /**
     * 상태 변경(읽고싶음 → 읽는중 → 완독 등) + 완독 시각 유지.
     *
     * <p>완독 진입 시 {@code now}를 기록하고(재완독이면 새 시각), 완독 이탈 시 지운다.
     * 완독→완독 재저장은 기존 시각을 보존한다(멱등 — 시각이 밀리지 않음). 단, 완독인데 시각이
     * 없으면(완독 상태로 방금 등록된 책, 또는 백필 전 레거시) {@code now}로 채운다 — 등록 경로의
     * 서비스 스탬프가 이 분기를 쓴다.
     *
     * @param now 현재 시각(필수) — 서비스가 Clock에서 받아 넘긴다
     */
    public void changeStatus(BookStatus newStatus, Instant now) {
        if (newStatus == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (newStatus == BookStatus.FINISHED) {
            if (this.status != BookStatus.FINISHED || this.finishedAt == null) {
                this.finishedAt = now;
            }
            // 첫 완독만 스탬프 — 이탈·재완독에도 안 민다(피드 "완독했어요"는 책당 1회).
            if (this.firstFinishedAt == null) {
                this.firstFinishedAt = now;
            }
        } else {
            this.finishedAt = null;
        }
        // 읽는중 "진입"에만, 그것도 <b>책당 1회</b> 스탬프 — 재독(완독→읽는중)도 재스탬프하지 않는다
        // (피드 재부상 방지, 2026-08-18 제품 결정). 읽는중→읽는중 재저장은 기존 값 보존(멱등)이고,
        // 완독으로 넘어갈 땐 손대지 않는다(시작·완독 두 이벤트 공존).
        // 전이 조건(status != READING)을 null 조건과 <b>함께</b> 두는 이유: 시작 시각이 없는 레거시
        // READING 책(V64 이전)이 no-op 재저장만으로 now에 스탬프돼 "방금 시작"으로 뜨는 걸 막는다.
        if (newStatus == BookStatus.READING && this.status != BookStatus.READING
                && this.startedReadingAt == null) {
            this.startedReadingAt = now;
        }
        this.status = newStatus;
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

    public String getCategory() {
        return category;
    }

    public String getPubDate() {
        return pubDate;
    }

    public BookStatus getStatus() {
        return status;
    }

    /**
     * 완독 축하를 보냈다고 표시한다 — 되돌리는 메서드는 일부러 두지 않는다(영구 1회).
     * 발송 성공 여부와 무관하게 전이 시점에 찍는다: 재시도 주체가 없어 "성공 시에만 마킹"으로 하면
     * 발송 실패 후 토글로 재발송을 유도할 수 있는 구멍이 도로 열린다.
     */
    public void markFinishCelebrated(Instant now) {
        this.finishCelebratedAt = now;
    }

    public Instant getFinishCelebratedAt() {
        return finishCelebratedAt;
    }

    public Instant getFirstFinishedAt() {
        return firstFinishedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getStartedReadingAt() {
        return startedReadingAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public long getCoupangClickCount() {
        return coupangClickCount;
    }

    public long getYes24ClickCount() {
        return yes24ClickCount;
    }

    public long getKyoboClickCount() {
        return kyoboClickCount;
    }

    public BookVisibility getVisibility() {
        return visibility;
    }
}
