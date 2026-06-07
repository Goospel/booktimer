-- V21 — 하루 목표 변경 이력(reading_goal_change) 테이블 + 백필.
--
-- 부채("이번 주 빠뜨린 날")는 max(0, 하루목표 − 그날 읽은 초)로 유도되는데, 하루 목표를 현재 평면값 하나
-- (reading_timer.daily_increment_seconds)로만 잡으면 사용자가 목표를 올렸을 때 옛 목표를 채웠던 과거 날이
-- 새 목표로 재판정돼 빠뜨린 날로 둔갑한다(소급 함정 — N-059). 목표가 설정·변경되는 시점마다 한 행을 남겨
-- 과거 날을 "그날 유효했던 목표"로 판정한다(GoalSchedule가 floorEntry로 해석).
--
-- 컬럼은 ReadingGoalChange 엔티티 매핑과 일치해야 한다(FlywayMigrationTest는 ddl-auto=validate라 어긋나면
-- 컨텍스트 로딩 실패). 시각은 BaseTimeEntity created_at/updated_at → datetime(6).
-- (user_id, effective_date) 유니크 = 하루 1행(같은 날 여러 번 바꾸면 그 행의 목표만 갱신=upsert).
--
-- 백필: 기존 사용자마다 "타이머 생성(가입)일부터 현재 목표"였다고 한 행을 시드한다 → 기존 동작을 보존하고
-- (가입~현재 전 구간이 현재 목표로 해석됨), 이 마이그레이션 이후의 목표 변경부터 시점별로 분기한다.
-- 이미 지나간 과거의 옛 목표는 데이터로 복원할 수 없어(예: 60→61분 변경 전 날들) 1분 미만 용서가 커버한다.
-- cast(x as date)·current_timestamp는 MySQL·H2(테스트) 공통 문법.

create table reading_goal_change (
    id             bigint      not null auto_increment,
    user_id        bigint      not null,
    effective_date date        not null,
    goal_seconds   bigint      not null,
    created_at     datetime(6) not null,
    updated_at     datetime(6) not null,
    primary key (id),
    constraint uk_reading_goal_change_user_date unique (user_id, effective_date),
    constraint fk_reading_goal_change_user foreign key (user_id) references users (id)
);

insert into reading_goal_change (user_id, effective_date, goal_seconds, created_at, updated_at)
select user_id, cast(created_at as date), daily_increment_seconds, current_timestamp, current_timestamp
from reading_timer;
