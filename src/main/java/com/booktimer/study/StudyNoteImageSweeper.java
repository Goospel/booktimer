package com.booktimer.study;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 매일 04:50 KST — 필기 사진 정리(백업 03:00 · 대화방 04:10 · 책 뉴스 04:30 뒤). 사진이 없으면 문장 셋. 두 색이 겹쳐 두 번 돌아도 멱등. */
@Component
public class StudyNoteImageSweeper {

    private static final Logger log = LoggerFactory.getLogger(StudyNoteImageSweeper.class);

    private final StudyNoteService noteService;
    private final Clock clock;

    public StudyNoteImageSweeper(StudyNoteService noteService, Clock clock) {
        this.noteService = noteService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 50 4 * * *", zone = "Asia/Seoul")
    public void run() {
        StudyNoteService.SweepResult r = noteService.sweepImages(clock.instant());
        if (r.unmarked() + r.deleted() + r.marked() > 0) {
            log.info("필기 사진 정리: 해제 {} · 삭제 {} · 표시 {}", r.unmarked(), r.deleted(), r.marked());
        }
    }
}
