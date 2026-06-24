package com.booktimer.garden;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 독서 마을 조회 유스케이스 — 작가(AUTHOR)·건물(BUILDING) 2축.
 *
 * <p>식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration)은 마을 컨셉 전환으로 제거됨.
 * DB 테이블은 보존(재매핑 후속).
 */
@Service
@Transactional(readOnly = true)
public class GardenService {

    private final AuthorCharacterRepository authorCharacterRepository;
    private final BuildingRepository buildingRepository;
    private final BookRepository bookRepository;

    public GardenService(AuthorCharacterRepository authorCharacterRepository,
                         BuildingRepository buildingRepository,
                         BookRepository bookRepository) {
        this.authorCharacterRepository = authorCharacterRepository;
        this.buildingRepository = buildingRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public GardenView view(User user) {
        List<Book> books = bookRepository.findByUserOrderByCreatedAtDesc(user);

        List<String> finishedAuthors = books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .map(Book::getAuthor)
                .toList();
        List<String> finishedPublishers = books.stream()
                .filter(b -> b.getStatus() == BookStatus.FINISHED)
                .map(Book::getPublisher)
                .toList();

        // 작가 캐릭터축 — 완독책 작가(정규화·contains)로 캐릭터 보유 유도
        Set<String> ownedAuthorNames = AuthorCharacterUnlockCalculator.normalizedAuthors(finishedAuthors);
        List<AuthorCharacter> authorCatalog = authorCharacterRepository.findAllByOrderByDisplayOrderAsc();
        List<AuthorCharacterState> authorCharacters =
                AuthorCharacterUnlockCalculator.resolve(authorCatalog, ownedAuthorNames);
        int ownedAuthorCharacterCount = (int) authorCharacters.stream().filter(AuthorCharacterState::owned).count();

        // 건물축 — 완독책 출판사 권수(정규화·contains)로 건물 보유 유도
        Map<String, Integer> publisherCounts = BuildingUnlockCalculator.publisherCounts(finishedPublishers);
        List<Building> buildingCatalog = buildingRepository.findAllByOrderByDisplayOrderAsc();
        List<BuildingState> buildings = BuildingUnlockCalculator.resolve(buildingCatalog, publisherCounts);
        int ownedBuildingCount = (int) buildings.stream().filter(BuildingState::owned).count();

        return new GardenView(
                authorCharacters, ownedAuthorCharacterCount, authorCatalog.size(),
                buildings, ownedBuildingCount, buildingCatalog.size());
    }
}
