package com.booktimer.migration;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.email.EmailToken;
import com.booktimer.email.EmailTokenRepository;
import com.booktimer.email.EmailTokenType;
import com.booktimer.garden.AuthorCharacterRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;

import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway 마이그레이션이 스키마를 관리함을 검증한다.
 *
 * <p>이전에는 {@code ddl-auto=update} + 기동 시 보정 코드(PasswordHashNullableSchemaFix)로
 * 스키마를 맞췄다. Flyway 도입 후엔 V1 마이그레이션이 스키마의 단일 소스다 — 이 테스트는
 * (1) V1이 적용됐고, (2) 소셜 계정(password_hash=null) INSERT가 되며, (3) 이메일 유니크가
 * 강제됨을 확인한다. H2(MySQL 모드)에서 마이그레이션이 실제로 실행되므로 SQL 자체도 검증된다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 메인 스위트는 Flyway를 끄고 Hibernate로 스키마를 만들지만, 이 테스트만 Flyway를 켜서
        // 실제 마이그레이션을 전용 격리 DB(MySQL 모드)에 적용한다. ddl-auto=validate로 두어,
        // V1 스키마가 엔티티 매핑과 어긋나면(드리프트) 컨텍스트 로딩이 실패하도록 한다.
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // Flyway(V2)가 세션 테이블을 만드므로 Spring Session 자동 초기화는 꺼서 CREATE 충돌을 막는다.
        "spring.session.jdbc.initialize-schema=never",
        "spring.datasource.url=jdbc:h2:mem:flyway_migration_test;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class FlywayMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailTokenRepository emailTokenRepository;

    @Autowired
    AuthorCharacterRepository authorCharacterRepository;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    StoryRepository storyRepository;

    @Autowired
    com.booktimer.auth.ApiTokenRepository apiTokenRepository;

    @Autowired
    com.booktimer.user.TossLinkCodeRepository tossLinkCodeRepository;

    @Autowired
    com.booktimer.session.StudyDailyCheckRepository studyDailyCheckRepository;

    @Test
    void v1_baseline_migration_is_applied() {
        boolean v1Applied = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .anyMatch(v -> v != null && v.getVersion().equals("1"));

        assertThat(v1Applied)
                .as("V1 baseline 마이그레이션이 적용되어야 한다")
                .isTrue();
    }

    @Test
    void social_account_with_null_password_can_be_persisted() {
        // password_hash 가 nullable이 아니면 여기서 제약 위반으로 실패한다.
        User social = User.ofOAuth("social@example.com", "소셜유저", "Asia/Seoul",
                Role.USER, AuthProvider.GOOGLE);

        User saved = userRepository.saveAndFlush(social);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPasswordHash()).isNull();
    }

    /**
     * V80 {@code uq_study_daily_check(user_id, check_date)} — 「하루 한 판정」 불변식의 최종 방어선.
     *
     * <p>여기(Flyway 스위트)에 있는 이유: 이 UNIQUE는 엔티티 {@code @Table}이 아니라 <b>마이그레이션에만</b>
     * 있다(이 레포는 DB를 제약의 단일 출처로 둔다 — {@code uk_users_login_id} 선례). 메인 스위트는
     * Hibernate가 엔티티 매핑에서 스키마를 만들어 <b>제약이 아예 없고, 없는 제약은 위반될 수도 없다</b>
     * — 거기 두면 영영 초록이 안 되는 공허한 테스트가 된다(T-169).
     */
    @Test
    void study_daily_check_unique_per_day_is_enforced() {
        User owner = userRepository.saveAndFlush(
                User.of("studycheck-dup@example.com", "hash", "닉", "Asia/Seoul", Role.USER));
        java.time.LocalDate date = java.time.LocalDate.of(2026, 8, 30);
        studyDailyCheckRepository.saveAndFlush(
                com.booktimer.session.StudyDailyCheck.of(owner, date, true));

        // 서비스는 조회-후-갱신으로 막지만, 경합에 진 두 번째 INSERT는 여기서 걸려야 한다.
        assertThatThrownBy(() -> studyDailyCheckRepository.saveAndFlush(
                com.booktimer.session.StudyDailyCheck.of(owner, date, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void email_unique_constraint_is_enforced() {
        userRepository.saveAndFlush(
                User.of("dup@example.com", "hash", "닉1", "Asia/Seoul", Role.USER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.of("dup@example.com", "hash", "닉2", "Asia/Seoul", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── login_id 무결성: onboarded ⟹ login_id IS NOT NULL (V15 CHECK, login-id-design PR-5) ──
    // 단순 NOT NULL은 OAuth와 충돌한다 — OAuth 사용자는 프로비저닝(INSERT) 시점엔 login_id가 없고
    // 온보딩에서 비로소 정한다. 그 전환 창에선 login_id=null이 정상이라, 조건부 불변식으로 좁힌다.

    @Test
    void onboarded_user_without_login_id_is_rejected() {
        // 온보딩 끝난 정식 계정인데 login_id가 비어 있으면 CHECK 위반이어야 한다.
        User u = User.of("onboarded-nologin@example.com", "hash", "닉", "Asia/Seoul", Role.USER);
        u.completeOnboarding();

        assertThatThrownBy(() -> userRepository.saveAndFlush(u))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void not_onboarded_user_without_login_id_is_allowed() {
        // OAuth 프로비저닝 직후 창 — 온보딩 전이면 login_id=null이 정상이다.
        User u = User.ofOAuth("pending-oauth@example.com", "소셜", "Asia/Seoul",
                Role.USER, AuthProvider.GOOGLE);

        User saved = userRepository.saveAndFlush(u);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLoginId()).isNull();
        assertThat(saved.isOnboarded()).isFalse();
    }

    // ── 이메일 인증 + 토큰(V31) — ddl-auto=validate라 컨텍스트 로딩만으로 엔티티↔스키마 드리프트가 잡힌다 ──

    @Test
    void new_user_defaults_to_email_unverified() {
        // V31의 email_verified 컬럼이 엔티티와 매핑되고, 신규 가입은 기본 미검증이어야 한다.
        // (기존 행 true 백필은 migration의 `update users set email_verified = true`가 담당 — V6 onboarded와 동일 패턴.)
        User u = User.of("verify-default@example.com", "hash", "닉", "Asia/Seoul", Role.USER);
        u.assignLoginId("vdefault");

        User saved = userRepository.saveAndFlush(u);

        assertThat(saved.isEmailVerified()).isFalse();
    }

    @Test
    void email_token_table_persists_under_flyway_schema() {
        // email_token 테이블(FK·컬럼)이 Flyway 스키마와 엔티티 매핑이 일치해야 저장된다(validate 모드).
        User owner = userRepository.saveAndFlush(
                userWithHandle("token-owner@example.com", "tokenowner"));
        EmailToken token = EmailToken.issue(owner, EmailTokenType.VERIFICATION,
                "a".repeat(64), Instant.parse("2026-06-12T00:00:00Z"));

        EmailToken saved = emailTokenRepository.saveAndFlush(token);

        assertThat(saved.getId()).isNotNull();
        assertThat(emailTokenRepository.findByTokenHash("a".repeat(64))).isPresent();
    }

    @Test
    void new_user_defaults_to_marketing_not_consented() {
        // V32의 marketing_email_consent 컬럼이 엔티티와 매핑되고(validate 모드 드리프트 검출), 신규 가입은
        // 기본 미동의(opt-in 불변식)여야 한다. email_verified와 달리 기존 행 백필(grandfather)이 없다 —
        // 동의한 적 없는 사용자를 동의로 채우는 건 동의 위조라, default false만 두고 update 백필을 하지 않는다.
        User u = User.of("nudge-default@example.com", "hash", "닉", "Asia/Seoul", Role.USER);
        u.assignLoginId("nudgedefault");

        User saved = userRepository.saveAndFlush(u);

        assertThat(saved.isMarketingEmailConsent()).isFalse();
        assertThat(saved.getMarketingConsentAt()).isNull();
        assertThat(saved.getLastNudgeSentAt()).isNull();
    }

    // ── 마을 작가 캐릭터 SVG 승격 전종 완료(V48 파일럿 → V49 나머지) — 전종 승격 불변식 ──
    // 파일럿(V48)은 한강 1종만 승격했고, V49가 나머지 작가 19종을 sprite_id=code로 채워 '전종 승격'을 완성한다.
    // 이제 author_character 전 행이 sprite_id=code(비null)여야 한다. (건물 축은 은퇴 — Java 엔티티 제거,
    // publisher_building 테이블·V48/V49 UPDATE는 적용 이력으로 보존하되 더는 검증하지 않는다.)
    // 이 가드는 V49의 UPDATE가 일부 code를 빠뜨리거나(미승격 잔존) sprite_id≠code로 채우면 깨진다.
    // 새 시드 행이 추가됐는데 sprite_id를 안 채운 경우도 여기서 잡힌다(N-055 — 미완성 엔티티 누수 가드).
    @Test
    void v49_promotes_all_characters_to_their_code_sprite() {
        var authors = authorCharacterRepository.findAll();

        assertThat(authors).isNotEmpty();

        // 전 작가가 sprite_id = code 로 승격(SVG 렌더 경로) — 미승격(null) 잔존 0.
        assertThat(authors)
                .allSatisfy(a -> assertThat(a.getSpriteId()).isEqualTo(a.getCode()));
    }

    // ── 여백 (V56 story · V71 책 귀속) — 스키마↔엔티티 일치 + 은퇴한 열람 테이블·NOT NULL 승격 ──
    // 아래 두 테스트가 여기 있는 이유: 메인 스위트는 Hibernate가 엔티티에서 스키마를 만들므로
    // 「story_view가 드롭됐는가」는 애초에 관측할 수 없고(엔티티 자체가 없다), 「book_id가 NOT NULL인가」도
    // 엔티티 매핑을 그대로 되읽는 공허한 단언이 된다. V71이 실제로 적용된 스키마만이 이 둘을 증명한다.

    @Test
    void story_table_persists_under_flyway_schema() {
        // story 테이블(FK·컬럼)이 Flyway 스키마와 엔티티 매핑이 일치해야 저장된다(validate 모드).
        User author = userRepository.saveAndFlush(userWithHandle("story-author@example.com", "storyauthor"));
        Book book = Book.register(author, "여백이 열린 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.saveAndFlush(book);

        Story story = storyRepository.saveAndFlush(Story.of(author, "마이그레이션 검증 문장", book, "night"));

        assertThat(story.getId()).isNotNull();
    }

    @Test
    void v71_retires_story_view_and_requires_book() {
        // V71: 열람 기록 은퇴(테이블 드롭) + 여백의 책 귀속(book_id NOT NULL). 둘 다 스키마에서 파생 단언.
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'STORY_VIEW'
                """, Integer.class))
                .as("story_view는 V71이 드롭했다 — 남아 있으면 죽은 테이블이 다음 사람의 인지 비용이 된다")
                .isZero();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'STORY' AND UPPER(COLUMN_NAME) = 'BOOK_ID'
                """, String.class))
                .as("여백은 책에 귀속된다 — book_id NOT NULL이 앱 코드 대신 DB가 지키는 제약")
                .isEqualTo("NO");
    }

    @Test
    void v77_story_shared_defaults_to_false_in_db() {
        // V77: 「함께 걸기」의 기본 꺼짐은 **DB가** 보장한다 — 앱을 거치지 않고 들어온 행(백필·수동 INSERT)도
        // 꺼져 있어야 「소급 노출 0」이라는 약속이 성립한다. 엔티티의 `= false` 초기화를 되읽는 것으로는
        // 이 층을 증명할 수 없으므로 스키마 기본값과 컬럼을 우회한 INSERT로 잰다.
        assertThat(jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'STORY' AND UPPER(COLUMN_NAME) = 'SHARED'
                """, String.class))
                .as("shared는 NOT NULL — 「모르는 상태」가 없어야 노출 판정이 3분기로 갈라지지 않는다")
                .isEqualTo("NO");

        User author = userRepository.saveAndFlush(userWithHandle("v77-author@example.com", "v77author"));
        Book book = Book.register(author, "V77 검증 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.saveAndFlush(book);
        jdbcTemplate.update("""
                INSERT INTO story (user_id, book_id, text, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, author.getId(), book.getId(), "shared를 안 적고 들어온 행");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT shared FROM story WHERE text = ?", Boolean.class, "shared를 안 적고 들어온 행"))
                .as("default false — 기존 글은 전부 꺼진 채로 남는다(소급 노출 0)")
                .isFalse();
    }

    // ── 토스 미니앱(V61) — users.toss_user_key 유니크 + api_token·toss_link_code 스키마↔엔티티 일치 ──

    @Test
    void toss_user_key_unique_constraint_is_enforced() {
        // 한 토스 신원이 두 계정에 붙으면 "어느 계정으로 로그인하나"가 모호해진다 — DB가 마지막 방어선.
        User first = userWithHandle("toss-uk-a@example.com", "tossuka");
        first.linkTossUserKey("uk-shared");
        userRepository.saveAndFlush(first);

        User second = userWithHandle("toss-uk-b@example.com", "tossukb");
        second.linkTossUserKey("uk-shared");

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void api_token_and_link_code_tables_persist_under_flyway_schema() {
        User owner = userRepository.saveAndFlush(userWithHandle("toss-token@example.com", "tosstoken"));
        Instant expiresAt = Instant.parse("2026-11-05T00:00:00Z");

        var token = apiTokenRepository.saveAndFlush(
                com.booktimer.auth.ApiToken.issue(owner, "b".repeat(64), expiresAt));
        var code = tossLinkCodeRepository.saveAndFlush(
                com.booktimer.user.TossLinkCode.issue(owner, "c".repeat(64), expiresAt));

        assertThat(token.getId()).isNotNull();
        assertThat(code.getId()).isNotNull();
        assertThat(apiTokenRepository.findByTokenHash("b".repeat(64))).isPresent();
        assertThat(tossLinkCodeRepository.findByCodeHash("c".repeat(64))).isPresent();
    }

    private static User userWithHandle(String email, String handle) {
        User u = User.of(email, "hash", "닉", "Asia/Seoul", Role.USER);
        u.assignLoginId(handle);
        return u;
    }

    @Test
    void onboarded_user_with_login_id_is_allowed() {
        // 정상 정식 계정 — login_id 보유 + 온보딩 완료.
        User u = User.of("onboarded-ok@example.com", "hash", "닉", "Asia/Seoul", Role.USER);
        u.assignLoginId("realhandle");
        u.completeOnboarding();

        User saved = userRepository.saveAndFlush(u);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLoginId()).isEqualTo("realhandle");
        assertThat(saved.isOnboarded()).isTrue();
    }

    /**
     * {@code AccountService.purge()}가 정리하는 테이블 목록 — users를 FK 참조하는 <b>모든</b> 테이블이어야 한다.
     *
     * <p>순서는 purge()의 삭제 순서와 무관하다(여기선 집합만 본다). 항목을 늘릴 땐 purge()에도 같이 넣어야 한다.
     */
    private static final Set<String> TABLES_PURGE_CLEARS = Set.of(
            "API_TOKEN", "AUTHOR_AFFECTION", "BLOCK", "BOOK", "EMAIL_TOKEN", "FEEDBACK", "FOLLOW",
            "READING_GOAL_CHANGE", "READING_GOAL_WAIVER", "READING_PERSONALITY", "READING_SESSION",
            "READING_TIMER", "REPORT", "STORY", "STORY_LIKE", "STUDY_BOOK", "STUDY_DAILY_CHECK",
            "STUDY_SESSION", "TOSS_LINK_CODE");

    /**
     * <b>users를 FK 참조하는 테이블 집합 == purge()가 지우는 집합</b>을 못 박는다.
     *
     * <p>왜 여기(마이그레이션 테스트)인가: 메인 스위트는 Hibernate가 <b>엔티티 매핑에서</b> 스키마를 만들어서
     * <b>JPA에 매핑되지 않은 테이블은 아예 존재하지 않는다</b>. 그런데 이 결함이 생긴 경로가 정확히 그것이었다 —
     * {@code garden_placement}·{@code garden_decoration_placement}·{@code push_subscriptions}는 Flyway에만
     * 있고 자바 코드가 한 번도 참조하지 않는 테이블인데, FK({@code NO ACTION})만 살아서 <b>탈퇴를 막고
     * 있었다</b>(운영 실측 2026-08-15: 27명 중 2명이 탈퇴 불가). 실제 마이그레이션 스키마를 보는 이 테스트만이
     * 그 부류를 잡는다.
     *
     * <p>그래서 이 단언은 <b>양방향</b>이다: FK가 새로 생겼는데 purge()에 없으면(=탈퇴가 깨진다) 실패하고,
     * 반대로 목록에만 있고 스키마에 없으면(=죽은 항목) 그것도 실패한다.
     */
    @Test
    void everyTableWithForeignKeyToUsersIsClearedByPurge() {
        Set<String> referencing = new HashSet<>(jdbcTemplate.queryForList("""
                SELECT DISTINCT fk.TABLE_NAME
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE fk
                  ON fk.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                 AND fk.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE pk
                  ON pk.CONSTRAINT_NAME = rc.UNIQUE_CONSTRAINT_NAME
                 AND pk.CONSTRAINT_SCHEMA = rc.UNIQUE_CONSTRAINT_SCHEMA
                WHERE UPPER(pk.TABLE_NAME) = 'USERS'
                """, String.class)).stream().map(t -> t.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        assertThat(referencing)
                .as("users를 FK 참조하는 테이블은 전부 AccountService.purge()가 지워야 한다 "
                        + "(빠지면 그 자식을 가진 사용자의 탈퇴가 FK 위반으로 실패한다)")
                .containsExactlyInAnyOrderElementsOf(TABLES_PURGE_CLEARS);
    }

    // ── 옛 핸들 영구 예약 (V69 uk_users_previous_login_id) ──
    // 이 두 테스트가 여기 있는 이유: 메인 스위트는 Hibernate가 스키마를 만들어 이 UNIQUE가 아예 없다
    // (login_id의 uk_users_login_id와 마찬가지로 엔티티 매핑에 선언하지 않는다 — DB가 단일 출처).
    // 슬라이스(@DataJpaTest)에 두면 제약이 없어 영영 초록이 안 되거나 공허해진다.

    @Test
    void previous_login_id_unique_constraint_is_enforced() {
        // 서비스의 사전 중복 검사를 우회해 DB 최종 방어선만 본다(경합 시 늦은 커밋이 여기서 막혀야 한다).
        User first = userWithHandle("prev-first@example.com", "sharedold");
        first.changeLoginId("prevfirstnew");
        userRepository.saveAndFlush(first);

        // 이제 sharedold는 비어 보이지만 예약돼 있다 — 남이 집어 다시 버리면 예약이 겹친다.
        User second = userWithHandle("prev-second@example.com", "sharedold");
        second.changeLoginId("prevsecondnew");

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onboarded_user_can_change_login_id() {
        // 아이디 변경은 login_id를 null로 만들지 않으므로 V15 CHECK(onboarded ⟹ login_id IS NOT NULL)를
        // 위반할 수 없다 — 정식 계정(온보딩 완료)에서 실제로 한 번 못 박는다.
        User u = userWithHandle("change-onboarded@example.com", "beforehandle");
        u.completeOnboarding();
        userRepository.saveAndFlush(u);

        u.changeLoginId("afterhandle");
        User saved = userRepository.saveAndFlush(u);

        assertThat(saved.getLoginId()).isEqualTo("afterhandle");
        assertThat(saved.getPreviousLoginId()).isEqualTo("beforehandle");
        assertThat(saved.isOnboarded()).isTrue();
    }

    @Test
    void unchanged_accounts_keep_null_previous_login_id() {
        // 미변경 계정은 previous_login_id가 NULL이다 — nullable+unique라 NULL은 여럿 허용된다
        // (전원 NULL이어도 무충돌). 두 계정을 나란히 저장해 그 사실을 못 박는다(null-state 경계).
        User a = userRepository.saveAndFlush(userWithHandle("nullprev-a@example.com", "nullpreva"));
        User b = userRepository.saveAndFlush(userWithHandle("nullprev-b@example.com", "nullprevb"));

        assertThat(a.getPreviousLoginId()).isNull();
        assertThat(b.getPreviousLoginId()).isNull();
    }
}
