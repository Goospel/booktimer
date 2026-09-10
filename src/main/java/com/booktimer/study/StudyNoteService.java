package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private final StudyNoteRepository noteRepository;
    private final RateLimitService rateLimitService;

    public StudyNoteService(StudyNoteRepository noteRepository, RateLimitService rateLimitService) {
        this.noteRepository = noteRepository;
        this.rateLimitService = rateLimitService;
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
     * @param revision 클라가 마지막으로 받은 {@code revision}
     * @throws ResponseStatusException  404 — 없거나 남의 필기(존재 비노출) /
     *                                  409 — 그 사이 다른 곳에서 고쳐졌다. <b>덮어쓰지 않는다</b>
     * @throws IllegalArgumentException 본문이 비었거나 길이 위반(→ 400). 생성과 <b>같은 규칙·같은 답</b>이다
     */
    public StudyNote update(User user, Long id, String title, String body, int revision) {
        StudyNote note = owned(user, id);
        try {
            note.edit(title, body, revision);
        } catch (StudyNote.StaleNoteException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "다른 곳에서 고쳐진 필기예요 — 새로고침한 뒤 이어서 써 주세요");
        }
        return noteRepository.save(note);
    }

    /** @throws ResponseStatusException 404 — 없거나 남의 필기(존재 비노출) */
    public void delete(User user, Long id) {
        noteRepository.delete(owned(user, id));
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
