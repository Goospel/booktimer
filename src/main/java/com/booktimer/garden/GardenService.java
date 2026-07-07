package com.booktimer.garden;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 서재 조회 유스케이스 — 작가(AUTHOR) 축.
 *
 * <p>건물(BUILDING)축은 작가 꾸미기 피벗으로 은퇴됨 — 식물 4축·소품에 이어 제거.
 * DB 테이블(publisher_building)은 보존(소프트 제거).
 */
@Service
@Transactional(readOnly = true)
public class GardenService {

    private final AuthorCharacterRepository authorCharacterRepository;
    private final BookRepository bookRepository;

    public GardenService(AuthorCharacterRepository authorCharacterRepository,
                         BookRepository bookRepository) {
        this.authorCharacterRepository = authorCharacterRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public GardenView view(User user) {
        List<Book> books = bookRepository.findByUserOrderByCreatedAtDesc(user);

        List<String> finishedAuthors = books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .map(Book::getAuthor)
                .toList();

        // 작가 캐릭터축 — 완독책 작가(정규화·contains)로 캐릭터 보유 유도
        Set<String> ownedAuthorNames = AuthorCharacterUnlockCalculator.normalizedAuthors(finishedAuthors);
        List<AuthorCharacter> authorCatalog = authorCharacterRepository.findAllByOrderByDisplayOrderAsc();
        List<AuthorCharacterState> authorCharacters =
                AuthorCharacterUnlockCalculator.resolve(authorCatalog, ownedAuthorNames);
        int ownedAuthorCharacterCount = (int) authorCharacters.stream().filter(AuthorCharacterState::owned).count();

        return new GardenView(authorCharacters, ownedAuthorCharacterCount, authorCatalog.size());
    }
}
