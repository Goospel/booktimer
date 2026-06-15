package com.booktimer.garden;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 작가 캐릭터 카탈로그 조회 — 정적 시드(V45) 읽기 전용. */
public interface AuthorCharacterRepository extends JpaRepository<AuthorCharacter, Long> {
    List<AuthorCharacter> findAllByOrderByDisplayOrderAsc();
}
