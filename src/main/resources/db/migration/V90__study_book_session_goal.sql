-- V90 — 공부 책별 「회당 시간」(한 번 앉을 때 공부할 시간, 초).
--
-- 공부 타이머의 「하루 목표」(users.study_daily_goal_seconds, V79)를 대체할 토대다. 목표가 날짜가 아니라
-- **책**에 붙는다 — 그 책으로 시작한 측정이 이 시간에 닿으면 알린다(측정은 멈추지 않는다).
--
-- null = 안 정함(제한 없는 스톱워치). 정하면 60 ≤ v ≤ 21600(측정 상한 6시간과 같다) —
-- 범위는 StudyBook.changeSessionGoal이 강제한다. 0은 「해제」가 아니라 잘못된 값이다(해제는 null만).
--
-- 하루 목표 컬럼은 여기서 지우지 않는다 — 라이브 미니앱 번들이 아직 읽으므로 새 번들 라이브 뒤 별도 마이그레이션.
alter table study_book add column session_goal_seconds int null;
