package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 필기의 <b>탈퇴 연쇄</b>만 재는 통합 테스트 (실 H2 — mock 금지, T-023·T-029).
 *
 * <p>{@code study_note}는 users와 study_book을 <b>둘 다</b> FK 참조한다. 그래서 purge에서 순서가 하나만
 * 틀려도(필기를 책보다 늦게 지우면) 필기를 가진 사람만 탈퇴가 제약 위반으로 실패한다 — 필기 없는
 * 사용자로 도는 다른 테스트는 전부 초록인 채로. mock은 FK를 아예 모른다.
 *
 * <p>{@code FlywayMigrationTest#everyTableWithForeignKeyToUsersIsClearedByPurge}가 목록 누락은 잡지만,
 * <b>순서</b>는 잡지 못한다(그쪽은 집합만 본다). 그 사각이 이 테스트의 몫이다.
 */
@SpringBootTest
@Transactional
class StudyNoteServiceTest {

    @Autowired AccountService accountService;
    @Autowired StudyNoteService noteService;
    @Autowired StudyNoteRepository noteRepository;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("필기를 가진 회원이 탈퇴해도 FK 위반 없이 정리된다 (필기 → 책 순서)")
    void withdrawal_purgesNotes() {
        User user = User.of("note-purge@booktimer.com", passwordEncoder.encode("rawpw1234"),
                "책벌레", "Asia/Seoul", Role.USER);
        user.assignLoginId("notepurge");
        userRepository.saveAndFlush(user);
        StudyBook book = studyBookRepository.saveAndFlush(
                StudyBook.register(user, "정보처리기사 실기", "저자", null, null, null, null));
        noteService.create(user, book, "3장 함수", "함수는 입력을 받아 출력을 낸다");

        assertThatCode(() -> {
            accountService.deleteAccount("note-purge@booktimer.com", "rawpw1234");
            assertThat(userRepository.findByEmail("note-purge@booktimer.com")).isEmpty();
        }).doesNotThrowAnyException();

        assertThat(noteRepository.count()).isZero();
    }

    // ── 필기 사진 (설계 2026-10-19 study-note-image-attach) ─────────────────────

    /** JPEG 머리(FF D8 FF)로 시작하는 작은 바이트 — 진짜 사진일 필요는 없다(서버는 머리만 본다). */
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired StudyNoteImageRepository imageRepository;

    private User member(String loginId) {
        User user = User.of(loginId + "@booktimer.com", passwordEncoder.encode("rawpw1234"),
                "책벌레", "Asia/Seoul", Role.USER);
        user.assignLoginId(loginId);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("REQ-05 · 사진을 가진 회원도 탈퇴하면 FK 위반 없이 사진까지 지워진다")
    void withdrawal_purgesNoteImages() {
        User user = member("imgpurge");
        StudyBook book = studyBookRepository.saveAndFlush(
                StudyBook.register(user, "정보처리기사 실기", "저자", null, null, null, null));
        noteService.create(user, book, null, "![](/api/study/notes/images/1)");
        Long imageId = imageRepository.saveAndFlush(
                StudyNoteImage.of(user, JPEG, java.time.Instant.parse("2026-10-19T00:00:00Z"))).getId();

        assertThatCode(() -> accountService.deleteAccount("imgpurge@booktimer.com", "rawpw1234"))
                .as("purge에 사진 표가 빠지면 사진을 가진 사람만 FK 위반으로 탈퇴가 실패한다")
                .doesNotThrowAnyException();

        assertThat(userRepository.findByEmail("imgpurge@booktimer.com")).isEmpty();
        assertThat(imageRepository.existsById(imageId)).isFalse();
    }

    // ── 정리 배치 (REQ-06) — 실 H2. 네이티브 SQL이라 목 금지. 밤은 sweepImages(T0)·(T0+24h)·(T0+48h)로 흉내 낸다 ──

    private static final java.time.Instant T0 = java.time.Instant.parse("2026-10-19T19:50:00Z");   // 04:50 KST

    private static java.time.Instant night(int n) {
        return T0.plus(java.time.Duration.ofHours(24L * n));
    }

    private Long image(User user) {
        return imageRepository.saveAndFlush(StudyNoteImage.of(user, JPEG, T0.minusSeconds(60))).getId();
    }

    private StudyNote note(User user, StudyBook book, String body) {
        return noteRepository.saveAndFlush(StudyNote.of(user, book, null, body));
    }

    private StudyBook bookOf(User user) {
        return studyBookRepository.saveAndFlush(StudyBook.register(user, "책", "저자", null, null, null, null));
    }

    private static String ref(Long id) {
        return "![](/api/study/notes/images/" + id + ")";
    }

    @Test
    @DisplayName("REQ-06 · 정리: 본문이 가리키는 사진은 몇 밤이 지나도 남는다")
    void sweep_keepsReferencedImage() {
        User user = member("sweepkeep");
        Long id = image(user);
        note(user, bookOf(user), "앞 " + ref(id) + " 뒤");

        for (int n = 0; n < 3; n++) {
            noteService.sweepImages(night(n));
        }

        assertThat(imageRepository.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("REQ-06 · 정리: 아무 필기도 안 가리키는 사진은 첫 밤엔 표시만 하고 다음 밤 지운다")
    void sweep_marksThenDeletesOrphan() {
        User user = member("sweeporphan");
        Long id = image(user);

        noteService.sweepImages(night(0));
        assertThat(imageRepository.existsById(id)).as("첫 밤엔 표시만 — 되돌리기 여유").isTrue();

        noteService.sweepImages(night(1));
        assertThat(imageRepository.existsById(id)).as("다음 밤엔 지운다 — 처리방침 「3일 안에」").isFalse();
    }

    @Test
    @DisplayName("REQ-06 · 정리: 표시된 뒤 다시 가리키면(되돌리기) 표시가 풀려 다음 밤에도 남는다")
    void sweep_unmarksWhenReferencedAgain() {
        User user = member("sweepundo");
        Long id = image(user);
        noteService.sweepImages(night(0));   // 표시

        StudyNote restored = note(user, bookOf(user), ref(id));   // 되돌리기
        noteService.sweepImages(night(1));
        assertThat(imageRepository.existsById(id)).isTrue();

        // 표시가 정말 풀렸는지 — 다시 빼면 처음부터 하루 여유를 다시 받아야 한다. 해제 없이 옛 표시(밤 0)가 남아 있으면
        // 지우는 문장의 미참조 재확인만으로는 위 단언이 통과하고, 여기서 다시 빼는 순간 하루 여유 없이 곧바로 지워진다.
        noteRepository.delete(restored);
        noteRepository.flush();
        noteService.sweepImages(night(2));
        assertThat(imageRepository.existsById(id)).as("새 표시는 오늘 밤 — 다음 밤까지 되돌리기 여유").isTrue();

        noteService.sweepImages(night(3));
        assertThat(imageRepository.existsById(id)).isFalse();
    }

    @Test
    @DisplayName("REQ-06 · 정리: images/12와 남의 필기는 내 사진 1을 살리지 않는다 — 정확히 images/1이면 산다(대조군)")
    void sweep_boundaryAndOwnerOnly() {
        User me = member("sweepme");
        User other = member("sweepother");
        Long target = image(me);
        Long control = image(me);
        // 접두 충돌(images/{target}2) · 남의 필기의 정확한 참조 — 둘 다 내 target을 살리면 안 된다.
        note(me, bookOf(me), "![](/api/study/notes/images/" + target + "2)\n" + ref(control));
        note(other, bookOf(other), ref(target));

        noteService.sweepImages(night(0));
        noteService.sweepImages(night(1));

        assertThat(imageRepository.existsById(target)).as("괄호 경계·주인 한정").isFalse();
        assertThat(imageRepository.existsById(control)).as("대조군 — 내 필기가 정확히 가리킨다").isTrue();
    }
}
