package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 독서 프로필 read 서비스 — 한 사용자의 책장/세션을 읽어 {@link ReadingProfileAggregator}로 사실을 집계한다(책BTI Phase 2).
 *
 * <p>본인용이다(공개 여부 무관) — 결과를 본인만 보므로 PRIVATE 책을 포함해도 누출이 없고, PRIVATE 기본인
 * 책장에서도 유용하다(reading-personality-design §7). 공개·매칭용(PUBLIC 책만) 프로필은 후속 단계.
 *
 * <p><b>완독(FINISHED) 책만 집계한다</b>(사용자 결정) — 책BTI는 "무슨 책을 <i>읽었는가</i>"로 사람의
 * 성격·가치관·취향을 짚는 것이라, 아직 안 읽은(읽고싶음)·읽는중 책은 성향 신호가 아니다. 그래서 읽고싶음/읽는중
 * 책은 권수·저자·장르·연대 분포 어디에도 넣지 않는다. (그 결과 프로필의 상태별 권수는 완독분만 채워지고
 * 읽는중/읽고싶음은 0, 완독률은 1.0 — 이 값들은 표시·LLM 입력에 쓰지 않는다.)
 *
 * <p>집계 로직 자체는 순수 함수(Aggregator)에 있고 여기선 레포지토리 배선만 한다 — 로직은 빠른 단위 테스트로,
 * 이 서비스는 배선·사용자 범위(교차 누출 없음)만 통합 테스트로 본다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingProfileService {

    private final BookRepository bookRepository;
    private final ReadingSessionRepository sessionRepository;

    public ReadingProfileService(BookRepository bookRepository, ReadingSessionRepository sessionRepository) {
        this.bookRepository = bookRepository;
        this.sessionRepository = sessionRepository;
    }

    /** 주어진 사용자의 독서 프로필(사실)을 집계해 돌려준다 — <b>완독(FINISHED) 책만</b>(본인용). */
    public ReadingProfile profileOf(User user) {
        List<Book> finishedBooks = bookRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED) // 완독 책만 성향 입력으로
                .toList();
        List<ReadingSession> sessions = sessionRepository.findByUser(user);
        return ReadingProfileAggregator.aggregate(finishedBooks, sessions);
    }
}
