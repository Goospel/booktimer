package com.booktimer.blog;

import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 블로그 글 쓰기 · 지우기 · 읽기(계획 2026-11-11-b1-blog-core §5.1 · §6.4). */
@Service
@Transactional
public class BlogPostService {

    static final String NOT_FOUND = "글을 찾을 수 없어요";

    private final BlogPostRepository postRepository;
    private final BlogSentenceRepository sentenceRepository;
    private final BlogSentenceRevRepository revRepository;

    public BlogPostService(BlogPostRepository postRepository, BlogSentenceRepository sentenceRepository,
                           BlogSentenceRevRepository revRepository) {
        this.postRepository = postRepository;
        this.sentenceRepository = sentenceRepository;
        this.revRepository = revRepository;
    }

    /** 글 지우기 — 허용 목록과 무관(내 데이터를 지우는 길은 늘 연다 · §4.8). 글 행 잠금이 첫 DB 문장(§4.11). */
    public void delete(User user, long id) {
        BlogPost post = postRepository.findForUpdate(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        revRepository.deleteBySentencePost(post);  // blog_sentence_rev.sentence_id FK — 문장보다 앞
        sentenceRepository.deleteByPost(post);     // blog_sentence.post_id FK — 글보다 앞
        postRepository.delete(post);
    }
}
