package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

/**
 * V105 — 책생글 M1: 앱 글({@code writing_draft}) → 여백 쪽지({@code story}) 복사(설계 2026-10-29-app-blog-margin §5.1 · J4).
 *
 * <p>제목 · 본문 · 질문 기록 중 하나라도 있는 글을 <b>전부</b> 옮긴다 — 책 없는 글(책을 지워 연결이 풀린 글)은 책 없는 쪽지로(C9).
 * 다 빈 글(v1이 [이 책으로 글쓰기]를 누를 때 만든 빈 행)만 남긴다. 생각 = 제목 · 본문이 둘 다 있으면 「제목 + 빈 줄 + 본문」,
 * 하나만 있으면 그것(최대 10,102자 ≤ 10,200). 질문 기록 · 원가 · 시각 · 내보낸 시각은 그대로, 판은 0. 새 쪽지 id를
 * {@code writing_draft.story_id}에 되적어 옛 JS의 GET이 {@code movedTo}로 갈 곳을 찾게 한다(J5).
 *
 * <p><b>왜 SQL이 아니라 자바인가</b>: 「빈 줄」(줄바꿈 둘)을 MySQL과 H2가 같은 뜻으로 읽는 SQL 글자가 없다 — {@code '\n'}은
 * MySQL만 줄바꿈으로 풀고, {@code CHAR(10)}은 MySQL 8에서 이진 문자열이 되며, 파일에 줄바꿈을 그대로 쓰면 체크아웃 줄끝
 * (Windows CRLF · 운영 LF)에 따라 값이 달라진다. 자바 문자열 {@code "\n\n"}은 어디서나 같다.
 */
public class V105__writing_draft_to_story extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        try (Statement select = c.createStatement();
             ResultSet d = select.executeQuery("""
                     select id, user_id, book_id, title, body, questions_json, ai_updated_at, ai_cost_micro_usd,
                            exported_at, created_at, updated_at
                     from writing_draft order by id""");
             PreparedStatement insert = c.prepareStatement("""
                     insert into story (user_id, book_id, text, shared, revision, ai_log, ai_updated_at, ai_cost_micro_usd,
                                        exported_at, from_draft_id, created_at, updated_at)
                     values (?, ?, ?, false, 0, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
             PreparedStatement backref = c.prepareStatement("update writing_draft set story_id = ? where id = ?")) {
            while (d.next()) {
                String title = blankToNull(d.getString("title"));
                String body = blankToNull(d.getString("body"));
                String questions = d.getString("questions_json");
                if (title == null && body == null && questions == null) {
                    continue; // 다 빈 글만 옮기지 않는다
                }
                long draftId = d.getLong("id");
                insert.setLong(1, d.getLong("user_id"));
                long bookId = d.getLong("book_id");
                if (d.wasNull()) {
                    insert.setNull(2, Types.BIGINT);
                } else {
                    insert.setLong(2, bookId);
                }
                insert.setString(3, title != null && body != null ? title + "\n\n" + body : title != null ? title : body != null ? body : "");
                insert.setString(4, questions);
                insert.setTimestamp(5, d.getTimestamp("ai_updated_at"));
                insert.setLong(6, d.getLong("ai_cost_micro_usd"));
                insert.setTimestamp(7, d.getTimestamp("exported_at"));
                insert.setLong(8, draftId);
                insert.setTimestamp(9, d.getTimestamp("created_at"));
                insert.setTimestamp(10, d.getTimestamp("updated_at"));
                insert.executeUpdate();
                try (ResultSet key = insert.getGeneratedKeys()) {
                    key.next();
                    backref.setLong(1, key.getLong(1));
                }
                backref.setLong(2, draftId);
                backref.executeUpdate();
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
