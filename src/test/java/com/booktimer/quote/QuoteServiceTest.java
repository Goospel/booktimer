package com.booktimer.quote;

import com.booktimer.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QuoteService 슬라이스 테스트 (@DataJpaTest, H2) — DB 백업 격언 적재/추가/삭제/랜덤.
 *
 * <p>격언은 더 이상 정적 JSON이 아니라 DB(`quote` 테이블)에 있다 — admin이 런타임에 추가/삭제하면
 * 재시작 없이 대시보드에 반영된다. 비어 있으면 대시보드가 깨지지 않게 폴백 격언을 돌려준다.
 */
@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화
class QuoteServiceTest {

    @Autowired
    private QuoteRepository repository;

    private QuoteService service() {
        return new QuoteService(repository);
    }

    @Test
    @DisplayName("add: 격언을 저장하고 id를 부여한다")
    void add_persistsQuote() {
        QuoteService service = service();

        Quote saved = service.add("새 문장", "새 작가");

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("all: 최신 등록이 먼저 오도록 정렬한다 (id 내림차순)")
    void all_listsNewestFirst() {
        QuoteService service = service();
        service.add("먼저 넣은 것", "A");
        service.add("나중에 넣은 것", "B");

        List<Quote> all = service.all();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getText()).isEqualTo("나중에 넣은 것");
    }

    @Test
    @DisplayName("delete: 해당 격언을 제거한다")
    void delete_removesQuote() {
        QuoteService service = service();
        Quote a = service.add("지울 것", "A");
        service.add("남길 것", "B");

        service.delete(a.getId());

        assertThat(service.all()).extracting(Quote::getText).containsExactly("남길 것");
    }

    @Test
    @DisplayName("add: 문장이 공백이면 거부한다")
    void add_rejectsBlankText() {
        assertThatThrownBy(() -> service().add("   ", "작가"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("add: 작가가 공백이면 거부한다")
    void add_rejectsBlankAuthor() {
        assertThatThrownBy(() -> service().add("문장", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("random: 저장된 격언이 있으면 그중 하나를 반환한다")
    void random_returnsStoredQuote() {
        QuoteService service = service();
        service.add("저장된 문장", "저장 작가");

        Quote picked = service.random();

        assertThat(picked.getText()).isEqualTo("저장된 문장");
        assertThat(picked.getAuthor()).isEqualTo("저장 작가");
    }

    @Test
    @DisplayName("random: 격언이 하나도 없으면 폴백 격언을 반환한다 (대시보드 깨짐 방지)")
    void random_returnsFallbackWhenEmpty() {
        QuoteService service = service();

        Quote picked = service.random();

        assertThat(picked).isNotNull();
        assertThat(picked.getText()).isNotBlank();
        assertThat(picked.getAuthor()).isNotBlank();
    }
}
