package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 공부 필기 유스케이스 — 목록 · 조회 · 생성 · 갱신 · 삭제.
 *
 * <p>AI를 안 쓰는 글쓰기라 승인 게이트·일일 몫이 없다({@link StudyRecallService}와 갈리는 자리). 대신 이
 * 서비스의 관심사는 셋이다: <b>소유권</b>(모든 조회가 {@code findByIdAndUser})과 <b>충돌</b>(자동저장이
 * 앞의 저장을 조용히 덮지 않게), 그리고 <b>생성의 상한</b>(자유 다장이라 자연 상한이 없다 — {@link #create}).
 */
@Service
@Transactional
public class StudyNoteService {

    private static final Logger log = LoggerFactory.getLogger(StudyNoteService.class);

    /** 「표시한 밤의 다음 밤」에 지워지게(매일 같은 시각) — 처리방침 5절 「3일 안에」와 짝이다(한 밤을 건너뛰어도 72시간). */
    static final Duration ORPHAN_GRACE = Duration.ofHours(20);

    static final String OUTDATED_EDITOR = "필기 화면이 새로 바뀌었어요 — 브라우저에서 이 페이지를 새로고침(F5)한 뒤 이어서 써 주세요";

    /** 판이 낡은 갱신·자동 삭제의 409 사유 — 화면 상태줄에 그대로 뜬다. */
    static final String STALE_NOTE = "다른 곳에서 고쳐진 필기예요 — 새로고침한 뒤 이어서 써 주세요";

    private final StudyNoteRepository noteRepository;
    private final StudyNoteImageRepository imageRepository;
    private final RateLimitService rateLimitService;
    private final Clock clock;

    public StudyNoteService(StudyNoteRepository noteRepository, StudyNoteImageRepository imageRepository,
                            RateLimitService rateLimitService, Clock clock) {
        this.noteRepository = noteRepository;
        this.imageRepository = imageRepository;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
    }

    /** 그 책의 필기 목록(최근 고친 순). 화면은 본문 없이 라벨·크기만 그린다. */
    @Transactional(readOnly = true)
    public List<StudyNote> list(User user, StudyBook book) {
        return noteRepository.findByUserAndBookOrderByUpdatedAtDescIdDesc(user, book);
    }

    /**
     * 백지복습 채점의 정답지 — 그 책의 필기를 최근순으로 {@link NoteReference#MAX_CHARS}까지.
     *
     * <p>분석({@code StudyRecallService.analyze})과 화면 카드({@code GET /api/study/notes/reference})가
     * <b>같은 이 문</b>을 지난다. 두 길이 각자 고르면 화면이 「들어간다」고 보여준 장과 모델이 실제로 본
     * 장이 어긋나고, 그 어긋남은 사용자에게 보이지 않는다.
     */
    @Transactional(readOnly = true)
    public NoteReference reference(User user, StudyBook book) {
        // ponytail: 그 책의 필기를 **본문째 전부** 메모리로 읽고 자른다(body는 basic 컬럼이라 eager).
        // 천장 = 한 책 수백 장 — 500장 × 8000자면 요청 하나가 약 8MB 문자열을 만들고, 이 EC2는 facefit과
        // 메모리를 나눠 쓴다. 게다가 이 왕복은 책 전환·저장마다 난다. 승급 경로 = 리포지터리에서 Top500
        // (또는 Pageable)으로 자르기 — 최근순 정렬이 이미 인덱스를 타므로 쿼리만 좁히면 된다.
        // 지금 안 하는 이유: 상한(MAX_CHARS)이 프롬프트 크기를 확실히 묶고 있어 「느려지면 그때」로 충분하다.
        return NoteReference.select(list(user, book), NoteReference.MAX_CHARS);
    }

    /** 내 필기 한 장 — 남의 것이면 빈 값이다(호출부가 404로 옮긴다). */
    @Transactional(readOnly = true)
    public Optional<StudyNote> find(User user, Long id) {
        return noteRepository.findByIdAndUser(id, user);
    }

    /**
     * 새 필기 한 장.
     *
     * <p><b>생성에만</b> 레이트리밋이 붙는다({@link RateLimitAction#STUDY_NOTE_CREATE} — 시간당 30).
     * 자연 상한이 없는 유일한 필기 경로라서다: 갱신은 같은 행을 고칠 뿐이고, 백지복습은
     * {@code UNIQUE(user, date)}로 하루 한 행이다. 갱신에 걸지 않는 것도 계약이다 — 자동저장이
     * 1.5초마다 두드리는 문이다.
     *
     * @throws ResponseStatusException  429 — 한도 초과. 무음 드롭이 아니라 안내다(작성은 콘텐츠 소실)
     * @throws IllegalArgumentException 책이 없거나 · 본문이 비었거나 · 길이를 넘는 경우(→ 400)
     */
    public StudyNote create(User user, StudyBook book, String title, String body) {
        if (!rateLimitService.allow(RateLimitAction.STUDY_NOTE_CREATE, user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "필기를 너무 자주 만들었습니다");
        }
        return noteRepository.save(StudyNote.of(user, book, title, body));
    }

    /**
     * 내 필기를 고친다 — <b>클라가 읽은 판이 아직 최신일 때만</b>.
     *
     * <p><b>소유권을 먼저</b> 확정하고 검증은 그 뒤다({@link #owned}가 404를 던진다) — 순서를 뒤집으면
     * 남의 필기가 400(길이 위반)을, 없는 필기가 404를 줘 그 차이로 존재 여부를 캐낼 수 있다(IDOR).
     *
     * <p>순서: 소유(404) → 옛 편집기 가드(409) → 판 대조(409)·검증(400).
     *
     * @param revision   클라가 마지막으로 받은 {@code revision}
     * @param imageAware 사진 노드를 켠 편집기(새 번들)만 참이다 — 거짓이면 사진 든 필기를 고칠 수 없다
     * @throws ResponseStatusException  404 — 없거나 남의 필기(존재 비노출) /
     *                                  409 — 그 사이 다른 곳에서 고쳐졌거나 옛 화면이다. <b>덮어쓰지 않는다</b>
     * @throws IllegalArgumentException 본문이 비었거나 길이 위반(→ 400). 생성과 <b>같은 규칙·같은 답</b>이다
     */
    public StudyNote update(User user, Long id, String title, String body, int revision, boolean imageAware) {
        StudyNote note = owned(user, id);
        if (!imageAware && StudyNoteImage.referencedIn(note.getBody())) {
            // 사진 노드를 모르는 편집기(열린 옛 번들 탭)는 사진 줄을 지운 채 저장한다 — 자동저장이라 사용자는 모른다(설계 §2-4 ①).
            // 플래그는 사진 노드를 켠 새 편집기만 싣는다. 옛 화면도 이 문구를 상태줄에 그대로 띄운다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, OUTDATED_EDITOR);
        }
        try {
            note.edit(title, body, revision);
        } catch (StudyNote.StaleNoteException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STALE_NOTE);
        }
        return noteRepository.save(note);
    }

    /**
     * 필기 사진 한 장 — 순서: 파트·크기·형식(400·413) → 레이트리밋(429) → 사람당 용량(400) → 전체 용량(503) → 저장.
     * 검증이 상한보다 먼저인 것은 전사와 같은 규율이다(잘못 만든 요청이 그 시간의 몫을 깎지 않게). 승인 게이트 없음(설계 D5).
     */
    public StudyNoteImage uploadImage(User user, MultipartFile file) {
        StudyNoteImage image = StudyNoteImage.of(user, readImage(file), clock.instant());
        if (!rateLimitService.allow(RateLimitAction.STUDY_NOTE_IMAGE, user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "사진을 너무 자주 올렸어요 — 잠시 뒤 다시 올려 주세요");
        }
        if (imageRepository.totalBytes(user) + image.byteSize() > StudyNoteImage.QUOTA_BYTES) {
            throw new IllegalArgumentException("사진은 한 사람당 200MB까지 둘 수 있어요 — 안 쓰는 사진이 든 필기를 지우면 며칠 안에 자리가 생겨요");
        }
        if (imageRepository.totalBytesAll() + image.byteSize() > StudyNoteImage.TOTAL_QUOTA_BYTES) {
            // 운영자가 볼 단서 — 이 줄이 찍히면 data 열을 S3로 옮길 때다(설계 §4-1). 503 본문은 화면이 그대로 띄운다(study/pure.ts errorMessage).
            log.warn("필기 사진 전체 상한 도달 — 업로드 거절(user {}). data 열을 S3로 옮길 때다", user.getId());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "지금은 사진을 더 넣을 수 없어요 — 서비스의 사진 저장 공간이 찼어요");
        }
        return imageRepository.save(image);
    }

    /** 내 사진일 때만 — 남의 것이면 빈 값이다(호출부가 404로 옮긴다, 존재 비노출). */
    @Transactional(readOnly = true)
    public Optional<StudyNoteImage> image(User user, Long id) {
        return imageRepository.findByIdAndUser(id, user);
    }

    /**
     * 사진 정리 — 순서가 계약이다: 풀기 → 지우기 → 표시. 표시가 마지막이라 오늘 처음 고아가 된 사진은 오늘 지워지지 않는다.
     * 바이트를 읽지 않는다. 두 색이 겹쳐 두 번 돌아도 멱등이다.
     */
    public SweepResult sweepImages(Instant now) {
        int unmarked = imageRepository.unmarkReferenced();
        int deleted = imageRepository.deleteOrphanedBefore(now.minus(ORPHAN_GRACE));
        int marked = imageRepository.markOrphans(now);
        return new SweepResult(unmarked, deleted, marked);
    }

    public record SweepResult(int unmarked, int deleted, int marked) { }

    /** 업로드를 검증하고 바이트로 읽는다 — JPEG 아님은 {@link StudyNoteImage#of}가 400으로 던진다. */
    private static byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("사진을 골라 주세요");
        }
        if (file.getSize() > StudyRecallService.MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진은 3MB 이하로 올려 주세요");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("사진을 읽을 수 없어요 — 다시 올려 주세요");
        }
    }

    /**
     * 내 필기를 지운다.
     *
     * <p>{@code revision}은 <b>자동 삭제</b>(화면이 저장된 장의 본문을 다 비웠다 — 설계 2026-10-19 note-empty-body-save)만 싣는다.
     * 그 사이 다른 곳(다른 탭·기기)에서 고쳐졌으면 409로 거절하고 <b>지우지 않는다</b> — 확인창이 없는 삭제라 갱신과 같은 규율이 필요하다.
     * 확인창을 거치는 「지우기」 버튼과 열린 옛 번들은 싣지 않는다(null — 지금처럼 지운다).
     *
     * <p>순서: 소유(404) → 판 대조(409) → 삭제. 뒤집으면 409/404 차이로 남의 필기 존재를 캐낼 수 있다({@link #update}와 같다).
     *
     * @throws ResponseStatusException 404 — 없거나 남의 필기(존재 비노출) / 409 — revision이 낡았다(지우지 않는다)
     */
    public void delete(User user, Long id, Integer revision) {
        StudyNote note = owned(user, id);
        if (revision != null) {
            try {
                note.requireRevision(revision);
            } catch (StudyNote.StaleNoteException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, STALE_NOTE);
            }
        }
        noteRepository.delete(note);
    }

    /**
     * 내 필기일 때만 반환 — 아니면(없음/남의 것) 404다. 존재 여부도 노출하지 않는다(IDOR 방지).
     *
     * <p>여기서 <b>바로</b> 404를 던지는 것이 요점이다. 예전엔 IAE를 던지고 컨트롤러가 모든 IAE를 404로
     * 옮겼는데, 그러면 같은 갈래에 섞여 오는 <b>입력 검증 IAE(빈 본문·길이 초과)까지 404로 뭉개졌다</b> —
     * 생성은 400을 주는데 갱신만 404를 주는 어긋남이었다. 갈래를 호출부가 아니라 <b>던지는 자리</b>에서
     * 가른다.
     */
    private StudyNote owned(User user, Long id) {
        return noteRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "필기를 찾을 수 없습니다"));
    }
}
