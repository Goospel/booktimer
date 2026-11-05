package com.booktimer.web.api;

import com.booktimer.book.BookSearchProbe;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 책 검색 상태 문(REQ-389) — {@code .github/workflows/uptime.yml}이 {@code deploy/health-probe.sh}로 본다.
 *
 * <p>{@code /api/public/**} = 익명 공개 계약이라 SecurityConfig 변경이 없다. 감시가 기억해 둔 결과만 읽으므로
 * 요청이 외부 호출을 일으키지 않는다(남용 걱정 없음). 본문은 상태 한 낱말뿐 — 키 · 검색어 · 예외 메시지를 싣지 않는다.
 * {@code /actuator/health}에 넣지 않는 이유: 외부 제공자 장애가 「서버가 죽었다」로 읽히고 배포 확인이 빨개진다.
 */
@RestController
public class BookSearchStatusApiController {

    private final BookSearchProbe probe;

    public BookSearchStatusApiController(BookSearchProbe probe) {
        this.probe = probe;
    }

    @GetMapping("/api/public/status/book-search")
    public ResponseEntity<Map<String, String>> status() {
        return probe.isDown()
                ? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"))
                : ResponseEntity.ok(Map.of("status", "UP"));
    }
}
