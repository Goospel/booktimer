-- 7일 윈도우 per-day 부채 모델 전환(PR #217) 후 남은 죽은 컬럼 제거.
--
-- 옛 모델: reading_timer 가 "단일 롤링 잔여(remaining_seconds) + 상한(cap_seconds) +
-- 매일 증가 Lazy accrual(last_accrual_date)"로 부채를 누적했다(N-001).
-- 새 모델: 부채는 reading_session 에서 유도(derive)하므로 이 세 컬럼은 더는 읽거나 쓰지 않는다.
-- 엔티티 매핑에서도 제거됐으므로(ReadingTimer) 컬럼을 안전하게 drop 한다.
-- 남는 상태는 daily_increment_seconds("하루 목표")뿐이다.
alter table reading_timer drop column remaining_seconds;
alter table reading_timer drop column cap_seconds;
alter table reading_timer drop column last_accrual_date;
