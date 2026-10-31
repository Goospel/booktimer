package com.booktimer.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 책생글 M1 — 여백 원장 마이그레이션(V104 비공개화 · 출처 스냅샷, V105 앱 글 → 쪽지 복사)을 실제 Flyway로 H2(MySQL 모드)에 돌린다.
 *
 * <p>V103까지 올린 DB에 운영 같은 행(공유 여백 · 좋아요 · 글 8종)을 넣고 나머지를 올린 뒤 행을 읽는다 — 마이그레이션 SQL·자바가
 * 실제로 도는지와 결과 행이 설계(§5.1 · J4)와 같은지를 함께 잰다.
 */
class MarginLedgerMigrationTest {

    private static final String BODY_10000 = "가".repeat(10_000);
    private static final String TITLE_100 = "제".repeat(100);

    private static String freshDb() {
        // 실행마다 새 DB — DB_CLOSE_DELAY=-1이라 이름을 재사용하면 앞 실행의 DB를 집는다(FlywayMigrationTest V98 주석)
        return "jdbc:h2:mem:margin_ledger_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private static long insert(Statement st, String sql) throws SQLException {
        st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
        try (ResultSet keys = st.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private static long user(Statement st, String email) throws SQLException {
        return insert(st, "insert into users (email, nickname, timezone, role, created_at, updated_at) values ('"
                + email + "', '닉', 'Asia/Seoul', 'USER', current_timestamp, current_timestamp)");
    }

    private static long book(Statement st, long userId, String title, String author) throws SQLException {
        return insert(st, "insert into book (user_id, title, author, status, visibility, created_at, updated_at) values ("
                + userId + ", '" + title + "', " + (author == null ? "null" : "'" + author + "'")
                + ", 'READING', 'PUBLIC', current_timestamp, current_timestamp)");
    }

    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("REQ-213 · V104 뒤 shared 0행 · 좋아요 0행 — 옮긴 문장이 있는 여백엔 『제목』 · 저자 출처가 백필된다(J84)")
    void v104_privatizesAndBackfillsQuoteSource() throws Exception {
        String url = freshDb();
        Flyway.configure().dataSource(url, "sa", "").target("103").load().migrate();
        long quoted;
        long plain;
        long noAuthor;
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            long owner = user(st, "m1-owner@example.com");
            long liker = user(st, "m1-liker@example.com");
            long demian = book(st, owner, "데미안", "헤르만 헤세");
            long anon = book(st, owner, "작자 미상 책", null);
            quoted = insert(st, "insert into story (user_id, book_id, text, quote, shared, created_at, updated_at) values ("
                    + owner + ", " + demian + ", '생각', '새는 알에서 나오려고 투쟁한다', true, current_timestamp, current_timestamp)");
            plain = insert(st, "insert into story (user_id, book_id, text, shared, created_at, updated_at) values ("
                    + owner + ", " + demian + ", '인용 없는 생각', true, current_timestamp, current_timestamp)");
            noAuthor = insert(st, "insert into story (user_id, book_id, text, quote, shared, created_at, updated_at) values ("
                    + owner + ", " + anon + ", '', '저자 없는 문장', false, current_timestamp, current_timestamp)");
            st.executeUpdate("insert into story_like (story_id, user_id, created_at, updated_at) values ("
                    + quoted + ", " + liker + ", current_timestamp, current_timestamp)");
            assertThat(count(st, "select count(*) from story where shared = true")).as("양성 대조 — 마이그레이션 전엔 있다").isEqualTo(2);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            assertThat(count(st, "select count(*) from story where shared = true")).isZero();
            assertThat(count(st, "select count(*) from story_like")).isZero();
            Map<Long, String> source = new HashMap<>();
            try (ResultSet rs = st.executeQuery("select id, quote_source from story")) {
                while (rs.next()) {
                    source.put(rs.getLong(1), rs.getString(2));
                }
            }
            assertThat(source.get(quoted)).isEqualTo("『데미안』 · 헤르만 헤세");
            assertThat(source.get(noAuthor)).isEqualTo("『작자 미상 책』");
            assertThat(source.get(plain)).as("옮긴 문장이 없으면 출처도 없다").isNull();
        }
    }

    @Test
    @DisplayName("REQ-215 · 픽스처 10행 대조 — 다 빈 글 · 공백뿐인 글만 0행 · 10,102자 그대로 · 책 없음은 book null · 원가 · 시각 · 내보낸 시각 · story_id 되적기")
    void v105_copiesDraftsToNotes() throws Exception {
        String url = freshDb();
        Flyway.configure().dataSource(url, "sa", "").target("103").load().migrate();
        Map<String, Long> draft = new HashMap<>();
        long bookId;
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            long owner = user(st, "m1-writer@example.com");
            bookId = book(st, owner, "데미안", "헤르만 헤세");
            String cols = "insert into writing_draft (user_id, book_id, title, body, revision, questions_json, ai_updated_at, "
                    + "ai_cost_micro_usd, exported_at, created_at, updated_at) values (" + owner + ", ";
            String at = "timestamp '2026-10-20 01:02:03', timestamp '2026-10-21 04:05:06')";
            draft.put("both", insert(st, cols + bookId + ", '제목', '본문', 3, null, null, 0, null, " + at));
            draft.put("bodyOnly", insert(st, cols + bookId + ", null, '본문만', 1, null, null, 0, null, " + at));
            draft.put("titleOnly", insert(st, cols + bookId + ", '제목만', '', 1, null, null, 0, null, " + at));
            draft.put("aiOnly", insert(st, cols + bookId + ", null, '', 0, '{\"rounds\":[]}', timestamp '2026-10-22 00:00:00', 1234, null, " + at));
            draft.put("empty", insert(st, cols + bookId + ", null, '', 0, null, null, 0, null, " + at));
            draft.put("exported", insert(st, cols + bookId + ", null, '내보낸 글', 2, null, null, 0, timestamp '2026-10-23 00:00:00', " + at));
            draft.put("noBook", insert(st, cols + "null, null, '책이 풀린 글', 1, null, null, 0, null, " + at));
            draft.put("max", insert(st, cols + bookId + ", '" + TITLE_100 + "', '" + BODY_10000 + "', 9, null, null, 0, null, " + at));
            // 공백뿐인 본문은 빈 것으로 친다(isBlank) — 줄바꿈 섞인 공백만 남은 글은 안 옮기고, 제목이 있으면 제목만
            draft.put("blankBody", insert(st, cols + bookId + ", null, '  \n ', 1, null, null, 0, null, " + at));
            draft.put("titleBlankBody", insert(st, cols + bookId + ", '제목', '   ', 1, null, null, 0, null, " + at));
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            assertThat(count(st, "select count(*) from story")).as("10행 중 다 빈 글 · 공백뿐인 글만 빠진다").isEqualTo(8);
            assertThat(count(st, "select count(*) from writing_draft where id = " + draft.get("empty") + " and story_id is null"))
                    .isEqualTo(1);
            assertThat(count(st, "select count(*) from writing_draft where id = " + draft.get("blankBody") + " and story_id is null"))
                    .as("공백뿐인 본문은 옮기지 않는다").isEqualTo(1);
            assertThat(text(st, draft.get("titleBlankBody"))).as("공백 본문은 붙이지 않는다").isEqualTo("제목");
            assertThat(text(st, draft.get("both"))).isEqualTo("제목\n\n본문");
            assertThat(text(st, draft.get("bodyOnly"))).isEqualTo("본문만");
            assertThat(text(st, draft.get("titleOnly"))).isEqualTo("제목만");
            assertThat(text(st, draft.get("aiOnly"))).isEmpty();
            assertThat(text(st, draft.get("max"))).hasSize(10_102).isEqualTo(TITLE_100 + "\n\n" + BODY_10000);
            try (ResultSet rs = st.executeQuery("select s.book_id, s.revision, s.ai_log, s.ai_cost_micro_usd, s.ai_updated_at, "
                    + "s.exported_at, s.created_at, s.updated_at, s.quote, s.shared from story s join writing_draft d on d.story_id = s.id "
                    + "where d.id = " + draft.get("aiOnly"))) {
                assertThat(rs.next()).as("story_id 되적기").isTrue();
                assertThat(rs.getLong(1)).isEqualTo(bookId);
                assertThat(rs.getInt(2)).as("revision 0").isZero();
                assertThat(rs.getString(3)).isEqualTo("{\"rounds\":[]}");
                assertThat(rs.getLong(4)).isEqualTo(1234L);
                assertThat(rs.getTimestamp(5).toString()).startsWith("2026-10-22 00:00:00");
                assertThat(rs.getTimestamp(6)).isNull();
                assertThat(rs.getTimestamp(7).toString()).startsWith("2026-10-20 01:02:03");
                assertThat(rs.getTimestamp(8).toString()).startsWith("2026-10-21 04:05:06");
                assertThat(rs.getString(9)).isNull();
                assertThat(rs.getBoolean(10)).isFalse();
            }
            try (ResultSet rs = st.executeQuery("select s.exported_at from story s join writing_draft d on d.story_id = s.id "
                    + "where d.id = " + draft.get("exported"))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp(1).toString()).startsWith("2026-10-23 00:00:00");
            }
            try (ResultSet rs = st.executeQuery("select s.book_id from story s join writing_draft d on d.story_id = s.id "
                    + "where d.id = " + draft.get("noBook"))) {
                assertThat(rs.next()).isTrue();
                rs.getLong(1);
                assertThat(rs.wasNull()).as("책 없는 글은 책 없는 쪽지로").isTrue();
            }
        }
    }

    private static String text(Statement st, long draftId) throws SQLException {
        try (ResultSet rs = st.executeQuery("select s.text from story s join writing_draft d on d.story_id = s.id where d.id = " + draftId)) {
            assertThat(rs.next()).as("draft " + draftId + "가 쪽지로 옮겨져야 한다").isTrue();
            return rs.getString(1);
        }
    }
}
