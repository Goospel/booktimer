package com.booktimer.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * User 영속성 저장소.
 *
 * <p>이메일은 로그인 식별자이자 유니크 키(uk_users_email)다(인증 컷오버 전까지). 닉네임은 더 이상 유니크가
 * 아니다 — 단순 표시 이름이라 중복을 허용한다. <b>식별·검색의 공개 핸들은 불변의 login_id</b>다(PR-3 컷오버
 * 완료, login-id-design.md §7): 프로필 조회·검색·팔로우/차단/신고 대상 식별이 모두 login_id 기준이다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * login_id(공개 @핸들)로 사용자 조회. login_id는 유니크·불변이라 1:1 식별의 정식 키다 —
     * 프로필({@code /u/{loginId}})·팔로우/차단/신고 대상 식별이 이걸로 이뤄진다.
     */
    Optional<User> findByLoginId(String loginId);

    /**
     * login_id 부분일치 검색(대소문자 무시), login_id 오름차순, <b>최대 20명</b>.
     * 검색은 닉네임이 아니라 <b>공개 @핸들(login_id) 기준</b>이다(인스타/X 모델). 결과 상한(Top20)으로
     * 열거·크롤링을 완화한다(sns-design §7.3·§9). 최소 길이 가드는 서비스가 담당.
     */
    java.util.List<User> findTop20ByLoginIdContainingIgnoreCaseOrderByLoginIdAsc(String loginId);

    /**
     * 토스 userKey로 사용자 조회 — 미니앱 로그인의 신원 키(이메일이 아니다, 설계 §2.2). UNIQUE라 1:1 식별이고,
     * 미연결 계정은 {@code toss_user_key IS NULL}이라 이 조회에 절대 걸리지 않는다.
     */
    Optional<User> findByTossUserKey(String tossUserKey);

    boolean existsByEmail(String email);

    /** 정규화된 login_id가 이미 쓰이는지 — 온보딩에서 공개 핸들 확정 전 유니크 사전 확인(uk_users_login_id). */
    boolean existsByLoginId(String loginId);

    /**
     * 그 아이디를 누군가 <b>버리고 간 옛 핸들</b>로 예약하고 있는지(uk_users_previous_login_id, V69).
     * 직접 쓰지 말고 {@link #isLoginIdTaken}을 쓴다 — 두 컬럼을 따로 보면 한쪽을 빠뜨리기 쉽다.
     */
    boolean existsByPreviousLoginId(String previousLoginId);

    /**
     * 이 아이디를 쓸 수 있는가 — 현행 {@code login_id}뿐 아니라 <b>예약된 옛 핸들</b>({@code previous_login_id})도
     * 점유로 본다. login_id를 정하는 <b>모든 경로</b>(가입·온보딩·핸들 생성·아이디 변경)가 이 한 메서드로 수렴한다.
     *
     * <p><b>왜 한 곳인가</b>: {@code login_id} UNIQUE는 아이디 변경 순간 옛 값을 비워 주므로 <b>DB가 예약을
     * 지켜 주지 못한다</b>. 한 경로라도 {@code existsByLoginId}만 보면 제3자가 남이 놓아준 핸들을 주워 가고,
     * 아이디를 바꾼 사용자의 열린 세션(principal = 옛 핸들)이 브리지 조회로 그 남의 계정에 붙는다 —
     * 곧 계정 탈취다. 호출처에 가드를 흩뿌리지 않고 여기로 모아, 새 경로가 생겨도 예약이 자동으로 지켜지게 한다.
     *
     * <p>자기 자신과의 오탐은 없다: 가입·온보딩 경로는 아직 자기 login_id가 없고(그리고 {@code login_id}가
     * null이면 {@code previous_login_id}도 반드시 null이다 — 옛 핸들은 {@link User#changeLoginId}로만 생기고
     * 그건 login_id를 요구한다), 아이디 변경은 서비스가 "현재 아이디와 동일"을 이 검사보다 먼저 끊는다.
     *
     * @param normalizedLoginId {@link User#normalizeLoginId}로 정규화한 값
     */
    default boolean isLoginIdTaken(String normalizedLoginId) {
        return existsByLoginId(normalizedLoginId) || existsByPreviousLoginId(normalizedLoginId);
    }

    /**
     * 옛 login_id로 사용자 조회 — 아이디 변경 <b>전에 열린 세션</b>의 principal을 해석하는 브리지 전용이다
     * ({@code CurrentUserService}). 로그인 경로는 이 조회를 쓰지 않는다 — 옛 아이디로 새로 로그인하는 것은
     * 막혀야 하기 때문(크리덴셜은 즉시 새 아이디로 넘어간다).
     */
    Optional<User> findByPreviousLoginId(String previousLoginId);

    /**
     * 친구 추천 후보 — ADMIN·본인·공개핸들(login_id) 미설정·차단(양방향) 제외, 무작위 순서로 Pageable 상한만큼.
     * 필터·상한·랜덤을 모두 DB에서 처리(메모리 findAll + 후보당 차단 COUNT N+1 제거).
     */
    @Query("""
            select u from User u
            where u.role <> com.booktimer.user.Role.ADMIN
              and u.id <> :viewerId
              and u.loginId is not null
              and not exists (
                select 1 from Block b
                where (b.blocker.id = :viewerId and b.blocked.id = u.id)
                   or (b.blocker.id = u.id and b.blocked.id = :viewerId))
            order by function('rand')
            """)
    List<User> findRecommendCandidates(@Param("viewerId") Long viewerId, Pageable pageable);

    /** 역할별 사용자 수 — 운영 통계에서 "가입자 수"는 {@code Role.USER}만 세어 ADMIN이 지표를 부풀리지 않게 한다. */
    long countByRole(Role role);

    /** 역할 + 온보딩 완료 여부별 사용자 수 — 운영 통계의 "온보딩 완료자" 카드. */
    long countByRoleAndOnboarded(Role role, boolean onboarded);

    /**
     * 관리자 사용자 목록 검색 — login_id 또는 nickname 부분일치(대소문자 무시), 페이징.
     * <b>email로는 검색하지 않는다</b>(노출 최소화 — admin-data-lookup-design §2.1).
     */
    org.springframework.data.domain.Page<User> findByLoginIdContainingIgnoreCaseOrNicknameContainingIgnoreCase(
            String loginId, String nickname, org.springframework.data.domain.Pageable pageable);
}
