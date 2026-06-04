package com.booktimer.popularity;

import com.booktimer.book.BookRepository;
import com.booktimer.book.FollowScopeCount;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 팔로우 스코프 인기 카운트 서비스 단위 테스트 (Mockito) — 가드·매핑만.
 *
 * <p>집계 정확도(팔로우 스코프·PRIVATE 제외·distinct user·status 버킷)는 mock으로 검증 불가 →
 * {@link FollowScopePopularityIntegrationTest}가 실제 스키마로 본다(N-040 교훈).
 */
@ExtendWith(MockitoExtension.class)
class FollowScopePopularityServiceTest {

    @Mock
    private BookRepository bookRepository;
    @InjectMocks
    private FollowScopePopularityService service;

    private final User viewer = User.of("v@booktimer.com", "$2a$10$x", "뷰어", "Asia/Seoul", Role.USER);

    @Test
    @DisplayName("isbn이 null·빈·공백뿐이면 빈 맵을 돌려주고 리포지토리를 호출하지 않는다")
    void emptyOrBlankIsbns_returnEmpty_noRepoCall() {
        assertThat(service.countByIsbn(viewer, null)).isEmpty();
        assertThat(service.countByIsbn(viewer, List.of())).isEmpty();
        assertThat(service.countByIsbn(viewer, List.of("  ", ""))).isEmpty();

        verify(bookRepository, never()).followScopePopularity(any(), anyCollection());
    }

    @Test
    @DisplayName("리포지토리 프로젝션을 isbn → (원함, 읽음) 맵으로 매핑한다")
    void mapsProjectionsByIsbn() {
        FollowScopeCount row = mock(FollowScopeCount.class);
        when(row.getIsbn()).thenReturn("9788966262281");
        when(row.getWantCount()).thenReturn(2L);
        when(row.getReadCount()).thenReturn(3L);
        when(bookRepository.followScopePopularity(eq(viewer), anyCollection())).thenReturn(List.of(row));

        Map<String, FollowScopePopularity> result = service.countByIsbn(viewer, List.of("9788966262281"));

        assertThat(result).containsOnlyKeys("9788966262281");
        assertThat(result.get("9788966262281").wantCount()).isEqualTo(2L);
        assertThat(result.get("9788966262281").readCount()).isEqualTo(3L);
    }
}
