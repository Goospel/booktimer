package com.booktimer.garden;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 아바타로 쓸 도감 작가 캐릭터 선택 유스케이스.
 *
 * <p>요구: <b>파일 업로드 없이</b> 사용자가 도감에서 <b>보유(완독)한 작가</b>의 얼굴만 프로필 사진으로 쓴다.
 * 그래서 저장하는 건 작가 캐릭터 코드 하나뿐이고, 렌더는 기존 SVG 스프라이트({@code #sprite-{code}})를 재사용한다.
 *
 * <p><b>핵심 보안 경계 = 보유 검증</b>: 남이 안 모은 작가를 프로필로 박는 위조(IDOR)를 막기 위해
 * {@link FeedingService#feed}와 동일하게 {@link GardenService#view}의 보유 작가 목록 포함 여부로 검증한다.
 * 미보유 코드는 {@link IllegalArgumentException}으로 거부한다(먹이주기와 동일 계약).
 */
@Service
@Transactional
public class ProfileCharacterService {

    private final GardenService gardenService;
    private final UserRepository userRepository;

    public ProfileCharacterService(GardenService gardenService, UserRepository userRepository) {
        this.gardenService = gardenService;
        this.userRepository = userRepository;
    }

    /**
     * 프로필 아바타 작가를 선택(또는 해제)한다.
     *
     * <ul>
     *   <li>{@code code}가 null/공백 → 선택 해제(이니셜 폴백) — 보유 검증 불필요</li>
     *   <li>보유(해금)한 작가 → 선택 저장</li>
     *   <li>미보유 작가 → {@link IllegalArgumentException}(위조 방어)</li>
     * </ul>
     *
     * @param user 선택 주체(본인 데이터만)
     * @param code 작가 캐릭터 코드(author_character.code) — null/공백이면 해제
     * @throws IllegalArgumentException 보유하지 않은 작가 코드인 경우
     */
    public void select(User user, String code) {
        // 해제(이니셜 폴백)는 보유 검증 불필요 — 빈 입력을 먼저 처리하고 끝낸다.
        if (code == null || code.isBlank()) {
            user.clearProfileCharacter();
            userRepository.save(user);
            return;
        }
        // 보유(해금) 작가인지 검증 — 미보유 작가 위조(IDOR) 방어. FeedingService.feed와 동일 경로.
        boolean owned = gardenService.view(user).ownedCharacters().stream()
                .anyMatch(c -> c.code().equals(code));
        if (!owned) {
            throw new IllegalArgumentException("보유하지 않은 작가입니다: " + code);
        }
        user.selectProfileCharacter(code);
        userRepository.save(user);
    }
}
