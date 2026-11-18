package com.booktimer.blog;

import com.booktimer.blog.BlogViews.BlockedUser;
import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import com.booktimer.block.BlockService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * 앱에서 대상(글 · 댓글)으로 차단(B2b · 계획 §4.7 · J52 · J81 · App Store 1.2) — loginId 없는 앱 가입자도 받는다. 대상은 신고와 같은
 * 「공개 대상」({@link BlogTargets}) · <b>운영자(ADMIN) 글도 받는다</b>(블로그는 ADMIN 글을 모두에게 보인다 — 존재는 글이 이미
 * 드러냈다). 허용 목록과 무관(안전 기능). 차단 효과(팔로우 정리 · DM 닫기)는 {@link BlockService#block} 그대로.
 */
@Service
@Transactional
public class BlogBlockService {

    private final BlogTargets targets;
    private final BlockService blockService;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    BlogBlockService(BlogTargets targets, BlockService blockService, BlockRepository blockRepository, UserRepository userRepository) {
        this.targets = targets;
        this.blockService = blockService;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
    }

    /**
     * 공개 대상이 아니면(초안 · 가린 것 · 없는 id) 404 — 이름 0. 이미 차단했으면 같은 200(멱등). 두 번 누름의 유일 위반은 여기서
     * flush로 드러내 부르는 쪽(트랜잭션 밖)이 한 번 더 부르게 한다.
     */
    public BlockedUser blockAuthorOf(User viewer, BlogReport.Target type, long targetId) {
        if (type == null) {
            throw new IllegalArgumentException("차단할 대상을 골라 주세요");
        }
        User author = targets.publicAuthor(type, targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "차단할 대상을 찾을 수 없어요"));
        if (Objects.equals(author.getId(), viewer.getId())) {
            throw new IllegalArgumentException("나를 차단할 수는 없어요");
        }
        blockService.block(viewer, author);
        blockRepository.flush();
        return new BlockedUser(author.getId(), author.getNickname());
    }

    /**
     * 내가 차단한 사람 — 최근 차단 순(설정 「차단한 사람」).
     * shortcut: 상한 없음 · 행마다 차단 대상을 따로 읽는다(N+1 — 웹 {@code BlockService.list}와 같은 꼴), 한 사람의 차단이 수백을
     * 넘는 날 fetch join + 상한으로 바꾼다.
     */
    @Transactional(readOnly = true)
    public List<BlockedUser> list(User viewer) {
        return blockRepository.findByBlockerOrderByCreatedAtDesc(viewer).stream().map(Block::getBlocked)
                .map(u -> new BlockedUser(u.getId(), u.getNickname())).toList();
    }

    /** 풀기 — 관계가 없거나 없는 id여도 그대로(멱등 204). */
    public void unblock(User viewer, long userId) {
        userRepository.findById(userId).ifPresent(u -> blockService.unblock(viewer, u));
    }
}
