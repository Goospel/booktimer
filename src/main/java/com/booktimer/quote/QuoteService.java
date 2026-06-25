package com.booktimer.quote;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

/**
 * 작가 격언 제공·관리 — 대시보드 인사말 자리에 랜덤으로 띄울 한 줄을 고르고, 운영자 추가/삭제를 처리한다.
 *
 * <p><b>소스</b>는 DB(`quote` 테이블, {@link QuoteRepository})다. 운영자가 admin에서 추가/삭제하면
 * 재시작 없이 다음 대시보드 로드부터 반영된다. 초기 큐레이션 격언은 Flyway(V17) seed로 들어간다.
 *
 * <p><b>왜 매 호출 새로 뽑나</b>: 대시보드 인사말은 htmx 라이브 영역 밖이라 전체 페이지 로드 때만 다시
 * 그려진다. 캐시 없이 매 {@link #random()}마다 새로 뽑아야 "페이지를 띄울 때마다 바뀐다"가 성립한다.
 *
 * <p><b>빈 목록 폴백</b>: 운영자가 격언을 모두 지워도 대시보드가 깨지지 않게 {@link #FALLBACK} 한 줄을 돌려준다.
 *
 * <p><b>랜덤 이음새</b>: 선택은 {@link QuotePicker}(순수)에 위임하고 {@link Random}을 주입받아 테스트에서 결정적으로 검증한다.
 */
@Service
public class QuoteService {

    /** 격언이 하나도 없을 때 대시보드가 깨지지 않게 돌려주는 기본 한 줄(저장되지 않은 값). */
    private static final Quote FALLBACK = Quote.of("책은 한 권 한 권이 하나의 세계다.", "윌리엄 워즈워스");

    private final QuoteRepository repository;
    private final Random random;

    @Autowired
    public QuoteService(QuoteRepository repository) {
        this(repository, new Random());
    }

    /** 테스트용 — {@link Random}을 직접 주입해 선택을 결정적으로 검증한다. */
    QuoteService(QuoteRepository repository, Random random) {
        this.repository = repository;
        this.random = random;
    }

    /** 격언 하나를 랜덤으로 고른다(매 호출 새로 — 캐시 없음). 하나도 없으면 폴백을 돌려준다. */
    @Transactional(readOnly = true)
    public Quote random() {
        List<Quote> all = repository.findAll();
        return all.isEmpty() ? FALLBACK : QuotePicker.pick(all, random);
    }

    /**
     * 셔플된 격언 최대 {@code max}개(대시보드 슬롯머신 로테이션용 — 클라이언트가 순환 표시).
     * 매 호출 새로 셔플해 페이지 로드마다 순서가 달라진다. 하나도 없으면 폴백 1개를 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<Quote> randomList(int max) {
        List<Quote> picked = QuotePicker.pickList(repository.findAll(), random, max);
        return picked.isEmpty() ? List.of(FALLBACK) : picked;
    }

    /** 등록된 격언 전체 — 최신 등록 먼저(admin 목록용). */
    @Transactional(readOnly = true)
    public List<Quote> all() {
        return repository.findAllByOrderByIdDesc();
    }

    /**
     * 격언을 추가한다(운영자).
     *
     * @throws IllegalArgumentException 문장·작가가 공백인 경우({@link Quote#of})
     */
    @Transactional
    public Quote add(String text, String author) {
        return repository.save(Quote.of(text, author));
    }

    /** 격언을 삭제한다(운영자). 없는 id면 조용히 무시한다. */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
