package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 공부 필기 영속성.
 *
 * <p>{@link StudyRecallRepository}와 달리 <b>id로 찾는 문이 있다</b> — 하루 한 장이 아니라 자유 다장이라
 * (나, 날짜) 같은 자연키가 없기 때문이다. 그래서 조회는 언제나 {@link #findByIdAndUser}로 <b>소유자를
 * 함께</b> 건다: id만으로 찾는 메서드를 여기 두는 순간 IDOR이 열린다.
 */
public interface StudyNoteRepository extends JpaRepository<StudyNote, Long> {

    /** 내 필기일 때만 — 남의 것은 빈 값이라 호출부가 404로 옮긴다(존재 비노출). */
    Optional<StudyNote> findByIdAndUser(Long id, User user);

    /**
     * 그 책의 필기 전부, 최근 고친 것부터. 정답지(§NoteReference)와 화면 목록이 같은 순서를 본다.
     *
     * <p>{@code id DESC}가 <b>동률을 깬다</b>. {@code updated_at}은 마이크로초라 충돌이 드물지만, 정답지가
     * 이 순서로 상한에 걸릴 장을 고르므로 동률의 상대 순서가 미정의면 <b>요청마다 채점 기준이 흔들린다</b> —
     * 화면이 「들어간다」고 보여준 장과 모델이 실제로 본 장이 어긋날 수 있다.
     */
    List<StudyNote> findByUserAndBookOrderByUpdatedAtDescIdDesc(User user, StudyBook book);

    /** 책 삭제 차단의 근거 — 0장일 때만 책을 뺄 수 있다({@code StudyBookService.delete}). */
    long countByBook(StudyBook book);

    /** 회원 탈퇴 시 정리(FK: study_note.user_id → users). study_book보다 <b>앞</b>에 불려야 한다. */
    void deleteByUser(User user);
}
