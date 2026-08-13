package com.booktimer.book;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 책 뉴스 캐시 영속성. 사용자·책 행과 연관이 없는 isbn 스코프 테이블이다({@link BookNews} 참고).
 */
public interface BookNewsRepository extends JpaRepository<BookNews, Long> {

    /**
     * 한 isbn의 캐시를 비운다 — 수집기의 delete-then-insert 앞단.
     *
     * <p>파생 삭제(select 후 엔티티 remove)가 아니라 <b>벌크 delete</b>인 이유: 파생 삭제는 같은
     * flush에 삽입과 섞여 Hibernate의 액션 순서(insert 먼저)에 걸리면 {@code (isbn13, link)} 유니크를
     * 위반할 수 있다. 벌크 쿼리는 즉시 실행돼 "지우고 → 넣는" 순서를 보장한다.
     */
    @Modifying
    @Transactional
    @Query("delete from BookNews n where n.isbn13 = :isbn13")
    void deleteByIsbn13(@Param("isbn13") String isbn13);

    /**
     * 여러 isbn의 기사를 최신 발행순으로. 홈 「책 뉴스」가 <b>내 완독 책의 isbn 집합</b>으로 부르며,
     * 상한은 {@code Pageable}로 건다(페이지네이션 없음 — 상한까지 한 번에 준다).
     */
    List<BookNews> findByIsbn13InOrderByPublishedAtDesc(Collection<String> isbn13s, Pageable pageable);
}
