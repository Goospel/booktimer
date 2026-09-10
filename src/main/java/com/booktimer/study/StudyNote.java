package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 공부 필기 한 장 — 책을 보며 그때그때 적은 것.
 *
 * <p>{@link StudyRecall}과 두 군데가 갈린다. ① <b>날짜가 없다</b>: 정리 축은 「그날」이 아니라 <b>책</b>이고
 * 한 책에 몇 장이든 쓸 수 있다(자유 다장 — 백지복습의 {@code UNIQUE(user, recall_date)} 같은 제약이 없다).
 * ② <b>책이 필수다</b>: 책이 채점 기준(백지복습 분석의 정답지)으로 가는 유일한 연결고리라, 책 없는 필기는
 * 어느 목록에도 안 뜨고 영영 채점에 안 들어가는 <b>조용한 누락</b>이 된다. 그래서 책 삭제도 참조를 푸는
 * 대신 409로 막는다({@code StudyBookService.delete}).
 *
 * <p>{@link #revision}은 자동저장의 낙관적 판 번호다 — 편집기가 1.5초마다 저장하므로 두 탭이 같은 필기를
 * 열면 늦은 쪽이 앞의 긴 필기를 <b>조용히 덮는다</b>. {@link #edit}이 「클라가 본 판」과 대조해 그 자리에서
 * 거절하는 것이 이 필드의 존재 이유다. JPA {@code @Version}을 안 쓰는 이유는 겨누는 대상이 다르기
 * 때문이다 — 우리가 잡아야 하는 것은 요청 사이의 어긋남이지 한 트랜잭션 안의 경합이 아니다.
 */
@Entity
@Table(name = "study_note")
public class StudyNote extends BaseTimeEntity {

    /**
     * 본문 상한 — {@link StudyRecall#BODY_MAX}와 <b>같은 값</b>이다. 편집기({@code RecallEditor})를 그대로
     * 재사용하는데 그쪽 예산 표시가 8000을 하드코딩해서다. 길면 새 장을 쓰면 된다(자유 다장이 흡수한다).
     */
    public static final int BODY_MAX = 8000;

    /** 제목 상한 — 선택 입력이라 목록 라벨 길이면 충분하다. */
    public static final int TITLE_MAX = 200;

    /**
     * 낡은 판으로 덮어쓰려 했다 — 서비스가 409로 옮긴다.
     *
     * <p>런타임 예외인 것이 의도다: 이건 「호출부가 복구할 수 있는 예외 상황」이 아니라 <b>사용자에게
     * 알려야 하는 충돌</b>이고, 검사예외로 두면 자동저장 경로 전체에 try가 번진다.
     */
    public static class StaleNoteException extends RuntimeException {
        public StaleNoteException(int expected, int actual) {
            super("stale revision: expected=" + expected + " actual=" + actual);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private StudyBook book;

    @Column(length = TITLE_MAX)
    private String title;

    // @Lob을 쓰지 않는 이유는 StudyRecall.body와 같다(마이그레이션의 text를 H2가 CHARACTER VARYING으로
    // 보고해 ddl-auto=validate가 깨진다) — V85·V89 주석.
    @Column(nullable = false, length = BODY_MAX)
    private String body;

    /** 0부터 시작해 갱신 성공마다 +1. 클라가 자기가 읽은 값을 되보내고 {@link #edit}이 대조한다. */
    @Column(nullable = false)
    private int revision;

    protected StudyNote() {
        // JPA
    }

    /**
     * 새 한 장.
     *
     * @throws IllegalArgumentException 책이 없거나 · 본문이 비었거나 · 길이를 넘는 경우
     *                                  (문구가 그대로 400 본문이 된다)
     */
    public static StudyNote of(User user, StudyBook book, String title, String body) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (book == null) {
            // 책 필수가 이 엔티티의 첫 불변식이다 — 화면 문구를 그대로 던져 400 본문이 되게 한다.
            throw new IllegalArgumentException("책을 골라 주세요");
        }
        StudyNote note = new StudyNote();
        note.user = user;
        note.book = book;
        note.title = optionalTitle(title);
        note.body = requireBody(body);
        note.revision = 0;
        return note;
    }

    /**
     * 본문을 갈아 쓴다 — <b>기대 판이 맞을 때만</b>.
     *
     * <p>대조와 증가가 한 메서드인 것이 요점이다. 서비스가 비교하고 엔티티가 올리는 식으로 쪼개면
     * 「비교를 잊은 새 호출부」가 언제든 생기고, 그 경로는 조용히 마지막 쓰기 승리로 돌아간다.
     *
     * @param expectedRevision 클라가 마지막으로 읽은 판 번호
     * @throws StaleNoteException       그 사이 다른 곳에서 고쳐진 경우 — <b>본문은 손대지 않는다</b>
     * @throws IllegalArgumentException 본문이 비었거나 길이를 넘는 경우
     */
    public void edit(String title, String body, int expectedRevision) {
        if (expectedRevision != this.revision) {
            throw new StaleNoteException(expectedRevision, this.revision);
        }
        // 검증을 대입보다 먼저 끝낸다 — 제목이 길다고 본문만 바뀐 반쪽 상태가 남으면 안 된다.
        String nextTitle = optionalTitle(title);
        String nextBody = requireBody(body);
        this.title = nextTitle;
        this.body = nextBody;
        this.revision++;
    }

    /** 본문 글자 수 — 목록이 본문 없이 크기만 보여줄 때 쓴다(정답지 상한 계산의 단위이기도 하다). */
    public int chars() {
        return body.length();
    }

    // StudyRecall의 같은 규칙을 그대로 둔다 — 공용 유틸로 빼지 않는다(2곳뿐이고, 문구가 도메인마다 다르다).

    private static String requireBody(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("쓴 내용을 입력해 주세요");
        }
        String trimmed = value.strip();
        if (trimmed.length() > BODY_MAX) {
            throw new IllegalArgumentException("쓴 내용은 " + BODY_MAX + "자까지 쓸 수 있어요");
        }
        return trimmed;
    }

    /** 빈 제목은 {@code null}이다 — 「제목 없음」 라벨은 화면이 파생한다(서버는 친 값만 저장한다). */
    private static String optionalTitle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > TITLE_MAX) {
            throw new IllegalArgumentException("제목은 " + TITLE_MAX + "자까지 쓸 수 있어요");
        }
        return trimmed;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public StudyBook getBook() {
        return book;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public int getRevision() {
        return revision;
    }
}
