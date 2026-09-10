package com.booktimer.study;

import com.booktimer.book.StudyBook;
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
 * <p>AI를 안 쓰는 글쓰기라 승인 게이트·상한이 없다({@link StudyRecallService}와 갈리는 자리). 대신 이
 * 서비스의 관심사는 둘이다: <b>소유권</b>(모든 조회가 {@code findByIdAndUser})과 <b>충돌</b>(자동저장이
 * 앞의 저장을 조용히 덮지 않게).
 */
@Service
@Transactional
public class StudyNoteService {

    private final StudyNoteRepository noteRepository;

    public StudyNoteService(StudyNoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /** 그 책의 필기 목록(최근 고친 순). 화면은 본문 없이 라벨·크기만 그린다. */
    @Transactional(readOnly = true)
    public List<StudyNote> list(User user, StudyBook book) {
        return noteRepository.findByUserAndBookOrderByUpdatedAtDesc(user, book);
    }

    /** 내 필기 한 장 — 남의 것이면 빈 값이다(호출부가 404로 옮긴다). */
    @Transactional(readOnly = true)
    public Optional<StudyNote> find(User user, Long id) {
        return noteRepository.findByIdAndUser(id, user);
    }

    /**
     * 새 필기 한 장.
     *
     * @throws IllegalArgumentException 책이 없거나 · 본문이 비었거나 · 길이를 넘는 경우(→ 400)
     */
    public StudyNote create(User user, StudyBook book, String title, String body) {
        return noteRepository.save(StudyNote.of(user, book, title, body));
    }

    /**
     * 내 필기를 고친다 — <b>클라가 읽은 판이 아직 최신일 때만</b>.
     *
     * @param revision 클라가 마지막으로 받은 {@code revision}
     * @throws IllegalArgumentException 없는·남의 필기(→ 컨트롤러가 404) · 본문 비었거나 길이 위반(→ 400)
     * @throws ResponseStatusException  409 — 그 사이 다른 곳에서 고쳐졌다. <b>덮어쓰지 않는다</b>
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

    /** @throws IllegalArgumentException 없는·남의 필기(→ 컨트롤러가 404) */
    public void delete(User user, Long id) {
        noteRepository.delete(owned(user, id));
    }

    /** 내 필기일 때만 반환 — 아니면(없음/남의 것) 거부한다. 존재 여부도 노출하지 않는다(IDOR 방지). */
    private StudyNote owned(User user, Long id) {
        return noteRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("study note not found: " + id));
    }
}
