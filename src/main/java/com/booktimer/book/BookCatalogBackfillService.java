package com.booktimer.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 책BTI Phase 1b — 기존 책의 카탈로그 메타(장르·출간일)를 알라딘 ItemLookUp으로 백필한다.
 *
 * <p>Phase 1a는 <i>앞으로 검색으로 추가되는</i> 책만 적재한다. 이미 책장에 있는 책은 장르·출간일이 비어
 * 있으므로(기존 행), ISBN으로 단건 조회해 채운다. 대상은 <b>{@code category}가 null이고 isbn13이 있는 책</b>뿐 —
 * 이미 채워진 책은 건너뛰어(멱등) 재실행해도 안전하고, isbn 없는 책은 조회 키가 없어 제외한다.
 *
 * <p><b>왜 메서드 전체를 {@code @Transactional}로 감싸지 않나</b>: 백필은 후보당 외부 HTTP(ItemLookUp)를 부르는데,
 * 트랜잭션을 연 채 네트워크를 기다리면 DB 커넥션을 오래 점유한다(안티패턴). 그래서 조회·쓰기만 짧은 트랜잭션
 * (Spring Data 메서드)으로 두고, HTTP는 트랜잭션 밖에서 한다. 채운 책은 마지막에 {@code saveAll}로 한 번에 반영.
 */
@Service
public class BookCatalogBackfillService {

    private static final Logger log = LoggerFactory.getLogger(BookCatalogBackfillService.class);

    /** 알라딘 제휴 추적 마커 — 저장된 구매링크에 이게 없으면 무추적(includeKey=1 픽스 전 저장분). 재조회로 갱신 대상. */
    private static final String AFFILIATE_KEY_MARKER = "ttbkey=";

    private final BookRepository bookRepository;
    private final BookSearchClient searchClient;

    public BookCatalogBackfillService(BookRepository bookRepository, BookSearchClient searchClient) {
        this.bookRepository = bookRepository;
        this.searchClient = searchClient;
    }

    /**
     * 카탈로그 메타가 빈 책을 최대 {@code limit}권까지 알라딘 ItemLookUp으로 채운다.
     *
     * <p>검색 비활성(키 없음)이거나 {@code limit <= 0}이면 아무것도 하지 않고 남은 권수만 보고한다.
     *
     * @param limit 한 번에 처리할 최대 권수(외부 호출량·요청시간 통제)
     * @return 처리·갱신·미발견·잔여 권수 요약
     */
    public BackfillResult backfill(int limit) {
        if (!searchClient.isEnabled() || limit <= 0) {
            return new BackfillResult(0, 0, 0, bookRepository.countByCategoryIsNullAndIsbn13IsNotNull());
        }

        // 후보 조회(짧은 트랜잭션) — 이미 채워진 책·isbn 없는 책은 쿼리에서 제외됨(멱등·null-state 차단).
        List<Book> candidates = bookRepository.findByCategoryIsNullAndIsbn13IsNotNull(PageRequest.of(0, limit));

        List<Book> updated = new ArrayList<>();
        int notFound = 0;
        for (Book book : candidates) {
            // 외부 HTTP는 트랜잭션 밖에서 — 커넥션 점유 최소화.
            Optional<BookSearchResult> meta = searchClient.lookupByIsbn(book.getIsbn13());
            if (meta.isPresent() && (meta.get().category() != null || meta.get().pubDate() != null)) {
                book.applyCatalogMetadata(meta.get().category(), meta.get().pubDate());
                updated.add(book);
            } else {
                notFound++; // 알라딘이 메타를 못 줌 — null로 남겨 다음 실행에서 재시도(헛 write 없음).
            }
        }

        // 채운 책들을 한 번에 반영 — saveAll은 Spring Data가 자체 트랜잭션으로 감싼다(detached는 merge).
        if (!updated.isEmpty()) {
            bookRepository.saveAll(updated);
        }

        long remaining = bookRepository.countByCategoryIsNullAndIsbn13IsNotNull();
        log.info("책BTI 카탈로그 백필 — 조회 {}권, 채움 {}권, 미발견 {}권, 잔여 {}권",
                candidates.size(), updated.size(), notFound, remaining);
        return new BackfillResult(candidates.size(), updated.size(), notFound, remaining);
    }

    /**
     * 제휴 추적이 빠진 기존 구매링크를 알라딘 ItemLookUp(includeKey=1)으로 재조회해 TTBKey 실린 링크로 교체한다.
     *
     * <p>배경: {@code includeKey=1} 픽스는 <i>앞으로</i> 검색으로 저장되는 링크만 고친다. 이미 책장에 저장된 책은
     * TTBKey 없는 옛 링크를 영구 재생하므로(그 클릭은 제휴 귀속 0), ISBN으로 재조회해 갱신해야 한다.
     * 대상은 <b>purchaseLink에 {@code ttbkey=}가 없고 isbn13이 있는 책</b> — 이미 실린 책은 건너뛰어(멱등) 재실행 안전.
     *
     * <p>{@link #backfill}과 같은 트랜잭션 규율(외부 HTTP는 트랜잭션 밖, 마지막에 {@code saveAll})을 따른다.
     * 재조회 결과에도 여전히 ttbkey가 없으면(픽스 미배포·알라딘 미제공) 옛 링크를 유지하고 {@code notFound}로 센다 —
     * 무추적 링크로 덮어써 헛수고하지 않는다.
     *
     * @param limit 한 번에 처리할 최대 권수(외부 호출량·요청시간 통제)
     * @return 처리·갱신·미발견·잔여 권수 요약
     */
    public BackfillResult backfillPurchaseLinks(int limit) {
        if (!searchClient.isEnabled() || limit <= 0) {
            return new BackfillResult(0, 0, 0,
                    bookRepository.countByIsbn13IsNotNullAndPurchaseLinkIsNotNullAndPurchaseLinkNotContaining(AFFILIATE_KEY_MARKER));
        }

        List<Book> candidates = bookRepository
                .findByIsbn13IsNotNullAndPurchaseLinkIsNotNullAndPurchaseLinkNotContaining(AFFILIATE_KEY_MARKER, PageRequest.of(0, limit));

        List<Book> updated = new ArrayList<>();
        int notFound = 0;
        for (Book book : candidates) {
            Optional<BookSearchResult> meta = searchClient.lookupByIsbn(book.getIsbn13());
            String newLink = meta.map(BookSearchResult::purchaseLink).orElse(null);
            if (newLink != null && newLink.contains(AFFILIATE_KEY_MARKER)) {
                book.updatePurchaseLink(newLink);
                updated.add(book);
            } else {
                notFound++; // 재조회에도 ttbkey 없음 — 옛 링크 유지(무추적 링크로 덮어쓰지 않음, 다음 실행 재시도).
            }
        }

        if (!updated.isEmpty()) {
            bookRepository.saveAll(updated);
        }

        long remaining = bookRepository.countByIsbn13IsNotNullAndPurchaseLinkIsNotNullAndPurchaseLinkNotContaining(AFFILIATE_KEY_MARKER);
        log.info("알라딘 제휴링크 백필 — 조회 {}권, 갱신 {}권, 미발견 {}권, 잔여 {}권",
                candidates.size(), updated.size(), notFound, remaining);
        return new BackfillResult(candidates.size(), updated.size(), notFound, remaining);
    }
}
