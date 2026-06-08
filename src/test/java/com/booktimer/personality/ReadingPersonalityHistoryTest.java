package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 책BTI 분석 히스토리(최대 3칸) 도메인 테스트 — 보존·교체·보호·선택 불변식을 본다.
 *
 * <p>사용자 요청(2026-06-08): "다시 분석"을 돌려도 과거 서술이 사라지지 않고 최대 3개까지 남는다.
 * 그중 하나를 <b>대표(selected)</b>로 고르면 공개 책방에 노출되고 삭제에서 보호된다. 새 분석은 후보로만
 * 추가되고(자동 대표 아님), 4개가 되면 <b>고른 것을 빼고 나머지 중 가장 오래된 후보</b> 1개를 버린다.
 *
 * <p>핵심 불변식:
 * <ul>
 *   <li>유저당 항상 0~3행, 그중 selected는 정확히 0~1개</li>
 *   <li>교체 시 selected는 절대 버려지지 않는다(오래됐어도 보호)</li>
 *   <li>"다시 분석"은 기존 대표를 바꾸지 않는다(후보로만 추가)</li>
 *   <li>대표 선택은 본인 행만(IDOR 차단), LLM 호출 없음</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class ReadingPersonalityHistoryTest {

    @Autowired
    private ReadingPersonalityService service;
    @Autowired
    private ReadingPersonalityCacheRepository cacheRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;

    @MockitoBean
    private ReadingPersonalityNarrator narrator;

    private User newUser(String email) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    /** 공개+완독 책 n권 적재(성향 입력 = 공개 완독 책). */
    private void saveBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            Book b = Book.register(u, "책" + i, "저자" + i, null, null, null, null,
                    null, null, BookStatus.FINISHED);
            b.makePublic();
            bookRepository.save(b);
        }
    }

    private void narratorReturns(String narrative) {
        when(narrator.narrate(any())).thenReturn(Optional.of(new PersonalityNarration(narrative, List.of("태그"))));
    }

    private List<String> narrativesNewestFirst(User u) {
        return cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u).stream()
                .map(ReadingPersonalityCache::getNarrative).toList();
    }

    private Optional<String> selectedNarrative(User u) {
        return cacheRepository.findByUserAndSelectedTrue(u).map(ReadingPersonalityCache::getNarrative);
    }

    @Test
    @DisplayName("첫 '다시 분석': 1행이 생기고 그게 대표(selected)가 된다")
    void firstReanalyze_createsSelectedEntry() {
        User u = newUser("first@booktimer.com");
        saveBooks(u, 3);
        narratorReturns("첫 분석.");

        service.reanalyze(u);

        assertThat(cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u)).hasSize(1);
        assertThat(selectedNarrative(u)).contains("첫 분석.");
    }

    @Test
    @DisplayName("둘째 '다시 분석': 후보로만 추가되고 기존 대표는 그대로 유지된다(자동 대표 아님)")
    void secondReanalyze_addsCandidate_keepsSelection() {
        User u = newUser("second@booktimer.com");
        saveBooks(u, 3);

        narratorReturns("분석1.");
        service.reanalyze(u);
        narratorReturns("분석2.");
        service.reanalyze(u);

        assertThat(narrativesNewestFirst(u)).containsExactly("분석2.", "분석1.");
        // 대표는 여전히 첫 분석(새 건 후보로만 추가됨)
        assertThat(selectedNarrative(u)).contains("분석1.");
    }

    @Test
    @DisplayName("히스토리는 3개로 제한 — 4번째에서 '대표 빼고 가장 오래된 후보' 1개를 버린다")
    void historyCapsAtThree_evictsOldestNonSelected() {
        User u = newUser("cap@booktimer.com");
        saveBooks(u, 3);

        narratorReturns("분석1.");
        service.reanalyze(u); // selected
        narratorReturns("분석2.");
        service.reanalyze(u);
        narratorReturns("분석3.");
        service.reanalyze(u);
        narratorReturns("분석4.");
        service.reanalyze(u); // 4개 → 가장 오래된 후보(분석2.) 제거

        // 남는 3개: 대표(분석1.) + 분석3. + 분석4. (분석2.가 제거됨)
        assertThat(narrativesNewestFirst(u)).containsExactly("분석4.", "분석3.", "분석1.");
        assertThat(selectedNarrative(u)).contains("분석1."); // 대표는 가장 오래됐어도 보호됨
    }

    @Test
    @DisplayName("대표는 여러 번 교체를 거쳐도 보호된다 — 골라둔 게 오래돼도 절대 안 버려진다")
    void selectedSurvivesRepeatedEviction() {
        User u = newUser("protect@booktimer.com");
        saveBooks(u, 3);

        narratorReturns("A.");
        service.reanalyze(u); // A = 부트스트랩 대표
        narratorReturns("B.");
        service.reanalyze(u);
        narratorReturns("C.");
        service.reanalyze(u);
        // A,B,C — A가 대표. 사용자가 B를 대표로 바꾼다(A는 후보로 강등, 가장 오래된 후보가 됨).
        Long bId = cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u).stream()
                .filter(c -> c.getNarrative().equals("B.")).findFirst().orElseThrow().getId();
        service.select(u, bId);

        narratorReturns("D.");
        service.reanalyze(u); // A,B,C,D → 가장 오래된 후보 A 제거 (B는 대표라 보호)
        narratorReturns("E.");
        service.reanalyze(u); // B,C,D,E → 가장 오래된 후보 C 제거

        assertThat(narrativesNewestFirst(u)).containsExactly("E.", "D.", "B.");
        assertThat(selectedNarrative(u)).contains("B."); // 골라둔 B가 끝까지 생존
    }

    @Test
    @DisplayName("대표 선택은 공개 책방 노출 서술을 바꾼다(LLM 호출 없음)")
    void select_changesRepresentative_noLlm() {
        User u = newUser("rep@booktimer.com");
        saveBooks(u, 3);
        narratorReturns("분석1.");
        service.reanalyze(u);
        narratorReturns("분석2.");
        service.reanalyze(u);

        Long secondId = cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u).get(0).getId();
        service.select(u, secondId);

        assertThat(selectedNarrative(u)).contains("분석2.");
        // 유저당 대표는 정확히 1개 — 선택은 LLM을 부르지 않는다(reanalyze 2회만)
        verify(narrator, times(2)).narrate(any());
        assertThat(cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u).stream()
                .filter(ReadingPersonalityCache::isSelected).count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("IDOR: 남의 분석 행은 대표로 선택할 수 없다(조용히 무시, 상대 대표 불변)")
    void select_otherUsersEntry_isIgnored() {
        User a = newUser("a@booktimer.com");
        saveBooks(a, 3);
        narratorReturns("A의 분석1.");
        service.reanalyze(a);
        narratorReturns("A의 분석2.");
        service.reanalyze(a);
        // A의 후보(비대표) 행 하나
        Long aCandidate = cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(a).get(0).getId();

        User b = newUser("b@booktimer.com");
        saveBooks(b, 3);
        narratorReturns("B의 분석.");
        service.reanalyze(b);

        service.select(b, aCandidate); // B가 A의 행을 선택 시도 — 무시되어야 함

        // A의 대표는 여전히 첫 분석(B가 못 건드림)
        assertThat(selectedNarrative(a)).contains("A의 분석1.");
        // B는 자기 행만 대표
        assertThat(selectedNarrative(b)).contains("B의 분석.");
    }

    @Test
    @DisplayName("GET 진입(currentPersonality): 책장이 바뀌어도 자동 재생성하지 않는다('다시 분석'에서만)")
    void currentPersonality_doesNotRegenerateOnSignatureChange() {
        User u = newUser("noregen@booktimer.com");
        saveBooks(u, 3);
        narratorReturns("부트스트랩 분석.");

        service.currentPersonality(u); // 첫 진입 — 부트스트랩 1회 생성
        saveBooks(u, 2);               // 책장 변화(시그니처 달라짐)
        ReadingPersonality again = service.currentPersonality(u); // 그래도 재생성 안 함

        verify(narrator, times(1)).narrate(any()); // 부트스트랩 1회뿐
        assertThat(again.hasNarration()).isTrue();
        assertThat(again.narration().narrative()).isEqualTo("부트스트랩 분석.");
        assertThat(cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u)).hasSize(1);
    }

    @Test
    @DisplayName("GET 진입: 히스토리가 비었고 책이 충분하면 첫 1개를 만들어 대표로 둔다(부트스트랩, 1회만)")
    void currentPersonality_bootstrapsOnceThenCacheHit() {
        User u = newUser("boot@booktimer.com");
        saveBooks(u, 3);
        narratorReturns("부트스트랩.");

        service.currentPersonality(u);
        ReadingPersonality second = service.currentPersonality(u);

        verify(narrator, times(1)).narrate(any()); // 둘째 진입은 캐시 히트(재호출 없음)
        assertThat(second.narration().narrative()).isEqualTo("부트스트랩.");
        assertThat(cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u)).hasSize(1);
        assertThat(selectedNarrative(u)).contains("부트스트랩.");
    }

    @Test
    @DisplayName("'다시 분석'했는데 LLM 실패: 새 행을 안 만들고 기존 대표를 그대로 내보낸다(serve-stale)")
    void reanalyze_llmFails_keepsExistingNoNewRow() {
        User u = newUser("fail@booktimer.com");
        saveBooks(u, 3);
        narratorReturns("원래 분석.");
        service.reanalyze(u); // 성공 1행

        when(narrator.narrate(any())).thenReturn(Optional.empty()); // 이후 실패
        ReadingPersonality result = service.reanalyze(u);

        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("원래 분석."); // stale 제공
        assertThat(cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u)).hasSize(1); // 새 행 없음
    }

    @Test
    @DisplayName("history(): 최신순 + 대표 표시 + '책장 바뀜(stale)' 플래그를 담아 돌려준다")
    void history_returnsRowsNewestFirstWithSelectedAndStale() {
        User u = newUser("hist@booktimer.com");
        saveBooks(u, 3);
        narratorReturns("옛 분석.");
        service.reanalyze(u);            // stale 아님(현재 책장 기준)
        saveBooks(u, 1);                 // 책장 변화 → 위 분석이 stale이 됨

        List<PersonalityHistoryEntry> rows = service.history(u);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).narrative()).isEqualTo("옛 분석.");
        assertThat(rows.get(0).selected()).isTrue();
        assertThat(rows.get(0).stale()).isTrue(); // 분석 이후 책장이 바뀜
    }
}
