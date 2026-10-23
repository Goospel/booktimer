package com.booktimer.writing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * 글쓰기 AI 전역 월 원가 상한(v1 §4.5 · REQ-22) — 사고 차단기다.
 *
 * <p>상한은 프로퍼티다({@code StudyAiUsageService.dailyCap}과 같은 이유): 제품 규칙이 아니라 사고 대응 장치라 <b>배포 없이 0으로
 * 내려 전면 차단</b>할 수 있어야 한다. 월 키는 UTC다. 트랜잭션을 두르지 않는다 — 리포지터리 메서드가 자기 트랜잭션으로 즉시 커밋해야
 * 동시 요청이 서로의 가산을 본다(StudyAiUsageService와 같은 판단).
 *
 * <p>ponytail: 사전 확인이 「누적 &lt; 상한」 한 번이라 동시에 진행 중인 호출 수만큼 상한을 넘길 수 있다(최대 진행 중 호출 × 약
 * $0.12 — Sonnet FOLLOW 최악 추정). 사용자가 한 자리 수인 지금은 받아들인다. 넘침이 문제가 되면 예약(선차감 → 정산)으로.
 */
@Service
public class AiBudgetService {

    private static final Logger log = LoggerFactory.getLogger(AiBudgetService.class);

    private final AiSpendMonthRepository repository;
    private final long capMicroUsd;

    public AiBudgetService(AiSpendMonthRepository repository,
                           @Value("${booktimer.writing.monthly-budget-usd:20}") BigDecimal monthlyBudgetUsd) {
        this.repository = repository;
        this.capMicroUsd = monthlyBudgetUsd.movePointRight(6).longValue();
    }

    /** 이번 UTC 달 누적 &lt; 상한인가 — 상한 0이면 늘 {@code false}(전면 차단). */
    public boolean hasRoom(Instant now) {
        long spent = repository.findBySpendMonth(month(now)).map(AiSpendMonth::getMicroUsd).orElse(0L);
        return spent < capMicroUsd;
    }

    /** 이번 UTC 달 누적에 더한다 — 0 이하는 아무것도 하지 않는다. */
    public void add(Instant now, long microUsd) {
        if (microUsd <= 0) {
            return;
        }
        String month = month(now);
        if (repository.add(month, microUsd) == 1) {
            return;
        }
        // 첫 0은 「그 달 행이 없었다」 — 만들고 다시 더한다. 동시에 만들면 UNIQUE가 하나만 남긴다(T-261).
        try {
            repository.save(AiSpendMonth.of(month));
        } catch (DataAccessException e) {
            log.debug("월 원가 행 생성 경합 — 다시 가산: {}", e.toString());
        }
        repository.add(month, microUsd);
    }

    static String month(Instant now) {
        return YearMonth.from(now.atZone(ZoneOffset.UTC)).toString();
    }
}
