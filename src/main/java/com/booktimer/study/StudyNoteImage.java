package com.booktimer.study;

import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 공부 필기에 넣은 사진 한 장 — 브라우저가 긴 변 1568px JPEG로 줄여 올린 바이트(V99).
 *
 * <p>사진은 <b>필기가 아니라 사람</b>에게 붙는다(user_id 하나, note_id 없음). 필기 행은 첫 비공백 저장 때
 * 생기는데 사진은 그보다 먼저 올라오고, 되돌리기·장 사이 복사로 다른 장이 같은 사진을 다시 가리킬 수 있다.
 * 본문은 마크다운 {@code ![](/api/study/notes/images/{id})} 한 꼴로만 가리키고, 어느 필기도 안 가리키는 사진은
 * 매일 정리({@link StudyNoteImageSweeper})가 표시 → 다음 밤 삭제한다.
 */
@Entity
@Table(name = "study_note_image")
public class StudyNoteImage {

    /** 서빙 주소의 앞부분 — 컨트롤러 매핑 · 편집기 OWN_IMAGE_PATH · 정리 SQL(LIKE)이 이 꼴 하나를 본다. */
    public static final String PATH = "/api/study/notes/images/";

    /** 본문 속 사진 표식 — 편집기가 쓰는 꼴 하나(alt 없음). 화면 editor/pure.ts·notes.ts와 같은 꼴(같은 리터럴 표로 잰다). */
    static final Pattern MARKDOWN_REF = Pattern.compile("!\\[\\]\\(/api/study/notes/images/([1-9]\\d*)\\)");

    /** 사람당 보관 상한(설계 D4). 필기에서 빠진 사진은 정리 배치가 3일 안에 지워 자리가 돌아온다. */
    public static final long QUOTA_BYTES = 200L * 1024 * 1024;

    /**
     * 서비스 전체 상한(설계 D4) — 사람당 상한은 계정을 여럿 만들면(가입 10/시간/IP) 합계를 못 묶는다. 디스크는 facefit과 같이 쓴다.
     * ponytail: 업로드마다 표 전체 byte_size 합(BLOB은 페이지 밖이라 안 읽는다) · 동시 업로드 몇 장만큼은 넘칠 수 있다(대략 상한).
     * 여기에 닿는 날이 곧 data 열만 S3로 옮기는 날이다 — 서비스가 경고 로그를 남긴다.
     */
    public static final long TOTAL_QUOTA_BYTES = 2L * 1024 * 1024 * 1024;

    /** 줄머리 블록 표식 — 화면 notes.ts stripMarkers의 앞 두 단계와 <b>같은 정규식</b>(같은 리터럴 표로 잰다). */
    private static final Pattern BLOCK_MARKERS = Pattern.compile("^\\s*(?:#{1,6}\\s+|[-*>]\\s+|\\d+\\.\\s+)+");
    private static final Pattern TASK_BOX = Pattern.compile("^\\s*\\[[ xX]\\]\\s*");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    // ponytail: 사진 바이트가 MySQL에 산다 — 매일 덤프·탈퇴 purge·H2 테스트가 저절로 덮는 대가로 천장은 DB 크기다.
    // 전체 상한(TOTAL_QUOTA_BYTES, 2GB)에 닿거나 덤프가 눈에 띄게 길어지면 이 필드만 S3로 빼는 자리(id·주인·정리·본문 참조 꼴은 그대로).
    // @Lob — H2가 MEDIUMBLOB을 Types.BLOB으로 보고해 validate가 BLOB 매핑을 요구한다(2026-10-19 실측).
    // length = MEDIUMBLOB 최대 길이 — 없으면 MySQLDialect가 tinyblob(255바이트)을 기대해 MySQL validate가 깨진다(H2는 그대로 BLOB).
    // ⚠️ 이 엔티티를 여러 개 싣는 쿼리를 만들지 않는다(바이트가 다 힙에 올라온다) — 합계·삭제·정리는 리포지터리의 JPQL·SQL로만.
    @Lob
    @Column(nullable = false, length = 16_777_215)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 정리 배치가 「어느 필기도 안 가리킨다」를 처음 본 시각 — 다음 밤에도 그러면 지운다(되돌리기 여유). 정리 SQL만 쓴다. */
    @Column(name = "orphaned_at")
    private Instant orphanedAt;

    protected StudyNoteImage() {
        // JPA
    }

    /** @throws IllegalArgumentException JPEG가 아니면 — 문구가 그대로 400 본문(「JPG 사진만 올릴 수 있어요」) */
    public static StudyNoteImage of(User user, byte[] jpeg, Instant now) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (!isJpeg(jpeg)) {
            throw new IllegalArgumentException("JPG 사진만 올릴 수 있어요");
        }
        StudyNoteImage image = new StudyNoteImage();
        image.user = user;
        image.data = jpeg;
        image.byteSize = jpeg.length;
        image.createdAt = now;
        return image;
    }

    /** JPEG 머리(FF D8 FF) — Content-Type은 클라가 적는 값이라 믿지 않는다. */
    static boolean isJpeg(byte[] b) {
        return b != null && b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    /** 이 본문이 사진을 하나라도 가리키나 — 옛 편집기 가드(REQ-07). 이스케이프된 글자 {@code !\[\](…)}는 사진이 아니다. */
    public static boolean referencedIn(String body) {
        return body != null && MARKDOWN_REF.matcher(body).find();
    }

    /** 본문 속 사진 수 — 채점 카드(REQ-09). */
    static int countIn(String body) {
        return body == null ? 0 : (int) MARKDOWN_REF.matcher(body).results().count();
    }

    /**
     * 사진만 있는 줄인가 — 목록 preview가 건너뛴다(REQ-08). 빈 줄도, 사진 없이 표식만 있는 줄(`- [ ]`)도 아니다.
     * 줄머리 표식(`- `·`> `·`# `·`1. `·`- [ ] `)을 <b>먼저 걷고</b> 본다 — 안 걷으면 `- ![](…)`가 글 줄로 잡혀 그 줄이 이름이 되고,
     * 화면은 그 한 줄에서 「사진」을 만든다(열린 장은 본문 전체를 봐서 다음 글 줄이 이름 — 같은 장의 이름이 갈린다).
     */
    static boolean isPhotoOnly(String line) {
        String rest = TASK_BOX.matcher(BLOCK_MARKERS.matcher(line).replaceFirst("")).replaceFirst("");
        return MARKDOWN_REF.matcher(rest).find() && MARKDOWN_REF.matcher(rest).replaceAll("").isBlank();
    }

    int byteSize() {
        return byteSize;
    }

    /** 본문에 넣을 주소 — 컨트롤러 GET 매핑과 같은 꼴(REQ-04 테스트가 이 문자열을 그대로 GET해 잠근다). */
    public String src() {
        return PATH + id;
    }

    public Long getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }
}
