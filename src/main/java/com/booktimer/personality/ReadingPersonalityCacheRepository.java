package com.booktimer.personality;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 저장된 책BTI 분석 결과 영속성(Phase 4 → 히스토리). 사용자당 0~3행, 그중 selected는 0~1개.
 */
public interface ReadingPersonalityCacheRepository extends JpaRepository<ReadingPersonalityCache, Long> {

    /**
     * 사용자의 <b>대표(selected)</b> 분석 한 건(없으면 비어 있음). 공개 책방 노출·GET 진입 표시의 단일 출처.
     * 유저당 selected는 최대 1행이라 유니크하게 0~1건만 나온다.
     */
    Optional<ReadingPersonalityCache> findByUserAndSelectedTrue(User user);

    /**
     * 사용자의 분석 히스토리를 <b>최신순</b>으로(생성 시각 내림차순, 동률이면 id 내림차순) 돌려준다 — 화면 표시용.
     * id를 tiebreak으로 둬 같은 시각에 들어온 분석도 결정적으로 정렬한다(N-기록: 신고함과 동일 교훈).
     */
    List<ReadingPersonalityCache> findByUserOrderByGeneratedAtDescIdDesc(User user);

    /**
     * 사용자의 분석 히스토리를 <b>오래된 순</b>으로(생성 시각 오름차순, 동률이면 id 오름차순) 돌려준다 — 교체
     * (가장 오래된 후보 제거) 판단용. id tiebreak으로 같은 시각도 결정적으로 "가장 오래된" 행을 고른다.
     */
    List<ReadingPersonalityCache> findByUserOrderByGeneratedAtAscIdAsc(User user);

    /** 회원 탈퇴 시 해당 유저의 모든 분석 행을 제거한다(user_id FK 정리). */
    void deleteByUser(User user);
}
