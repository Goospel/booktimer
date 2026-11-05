package com.booktimer.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 책 검색 감시(REQ-389) — 10분마다 알려진 책 한 권을 조회해 검색 제공자가 살아 있는지 기억해 둔다.
 *
 * <p><b>왜</b>: 알라딘 OpenAPI가 2026-10-30에 끝났는데 서버는 그것을 「0건」으로 내보내 4일간 아무도 몰랐다(T-278).
 * 그래서 「0건」도 실패로 센다 — 「200 + 빈 결과」가 바로 그 고장의 꼴이다. 결과는
 * {@code GET /api/public/status/book-search}가 읽고, {@code .github/workflows/uptime.yml}이 그 문을 본다.
 *
 * <p>서버 안에서 재는 이유: 우리 서버 → 카카오 실경로(키 · SSM 값 · 서버 출구)를 한 번에 재고 키를 한 곳에만 둔다.
 * 한 번 깜빡임은 장애가 아니다 — 두 번 잇달아 실패해야 DOWN이다({@code deploy/health-probe.sh}와 같은 철학).
 */
@Component
public class BookSearchProbe {

    private static final Logger log = LoggerFactory.getLogger(BookSearchProbe.class);

    /** 데미안(민음사 세계문학전집 44) — 오래 팔리는 책이라 사라질 일이 적다. */
    static final String PROBE_ISBN = "9788937460449";

    private final BookSearchClient client;
    private volatile boolean down;   // 기동 직후는 UP(첫 점검 전 오탐 방지)
    private int failures;            // 운영에선 스케줄러 스레드 하나만 쓴다

    public BookSearchProbe(BookSearchClient client) {
        this.client = client;
    }

    // 테스트 컨텍스트는 첫 회를 하루 뒤로 민다(src/test/resources) — 테스트 중 스케줄러가 상태를 바꾸지 않게.
    @Scheduled(initialDelayString = "${booktimer.book-search.probe.initial-delay-ms:60000}", fixedDelay = 600_000)
    public void check() {
        if (!client.isEnabled()) {   // 운영에서 키가 빠졌다 = 장애
            down = true;
            return;
        }
        boolean ok;
        try {
            ok = client.lookupByIsbn(PROBE_ISBN).isPresent();
        } catch (BookSearchUnavailableException e) {
            ok = false;   // 원인은 어댑터가 이미 로그로 남겼다(키 · 검색어 없이)
        }
        failures = ok ? 0 : failures + 1;
        boolean wasDown = down;
        down = failures >= 2;
        if (down && !wasDown) {
            log.error("책 검색 감시 — 알려진 책을 두 번 잇달아 못 찾았다(DOWN)");
        }
        if (!down && wasDown) {
            log.info("책 검색 감시 — 다시 UP");
        }
    }

    public boolean isDown() {
        return down;
    }
}
