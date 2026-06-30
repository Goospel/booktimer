package com.booktimer.garden;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 독서 마을 서비스 배선 검증 (실제 빈·H2·자체 시드 카탈로그).
 *
 * <p>건물(BUILDING)축은 마을 컨셉 전환(작가 꾸미기 피벗)으로 은퇴됨 — AUTHOR 캐릭터 축 배선만 확인.
 */
@SpringBootTest
@Transactional
class GardenServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private AuthorCharacterRepository authorCharacterRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private GardenService gardenService;
    @Autowired
    private Clock clock;

    @BeforeEach
    void seedCatalog() {
        // 작가 캐릭터 — 배선 확인용 한 종. prod 시드(V45)와 디커플.
        authorCharacterRepository.save(AuthorCharacter.of("han_gang", "한강", "소설가 한강", "🪶", 1, null));
    }

    /** 주어진 작가·출판사·상태로 책 한 권을 등록한다. */
    private void registerBookWith(User user, String title, String author, String publisher, BookStatus status) {
        bookRepository.save(Book.register(user, title, author, null, null, publisher, null,
                null, null, status));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email) {
        return registrationService.register(email, "rawpw1234", "정원사", SEOUL, Role.USER, today());
    }

    // --- AUTHOR axis: 작가 캐릭터 배선 확인 ------------------------------------------

    @Test
    @DisplayName("큐레이션 작가 완독 → view().ownedCharacters()에 등장 (배회 캐릭터로 노출)")
    void authorCharacters_finishedAuthor_owned() {
        User user = register("garden-author-owned@booktimer.com");
        registerBookWith(user, "소설", "한강 (지은이)", null, BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(authorCharacter(view, "han_gang").owned()).isTrue();
        assertThat(view.ownedAuthorCharacterCount()).isEqualTo(1);
        // AUTHOR는 배회 전용 — ownedCharacters()로 노출
        OwnedCharacter ch = view.ownedCharacters().stream()
                .filter(c -> c.code().equals("han_gang"))
                .findFirst().orElseThrow();
        assertThat(ch.emoji()).isEqualTo("🪶");
    }

    @Test
    @DisplayName("읽고싶음 책만 있으면 작가 캐릭터 미owned — 완독만 집계(파밍 방지)")
    void authorCharacters_wantToReadOnly_notOwned() {
        User user = register("garden-author-want@booktimer.com");
        registerBookWith(user, "소설", "한강 (지은이)", null, BookStatus.WANT_TO_READ);

        GardenView view = gardenService.view(user);

        assertThat(authorCharacter(view, "han_gang").owned()).isFalse();
        assertThat(view.ownedAuthorCharacterCount()).isZero();
    }

    @Test
    @DisplayName("null 작가 책만 완독 → 작가 캐릭터 누수 0 (N-055)")
    void authorCharacters_nullAuthor_noLeak() {
        User user = register("garden-author-null@booktimer.com");
        registerBookWith(user, "수동등록", null, null, BookStatus.FINISHED);

        GardenView view = gardenService.view(user);

        assertThat(view.authorCharacters()).noneMatch(AuthorCharacterState::owned);
        assertThat(view.ownedAuthorCharacterCount()).isZero();
    }

    @Test
    @DisplayName("책이 없으면 작가 캐릭터 전부 미보유 (null-state 누수 가드)")
    void nullState_noBooksAllLocked() {
        User user = register("garden-empty@booktimer.com");

        GardenView view = gardenService.view(user);

        assertThat(view.ownedAuthorCharacterCount()).isZero();
        assertThat(view.authorCharacters()).noneMatch(AuthorCharacterState::owned);
        assertThat(view.ownedCharacters()).isEmpty();
    }

    private static AuthorCharacterState authorCharacter(GardenView view, String code) {
        return view.authorCharacters().stream()
                .filter(s -> s.character().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
