-- V67 — 리워드 광고 용서의 일일 1회 상한 제거(uk_goal_waiver_grant, V62에서 추가).
--
-- 부채의 7일 자동 소멸을 폐지해(같은 PR) 빚이 계속 누적되므로, 갚을 수단도 계속 열려 있어야 한다.
-- 어뷰징 상한 역할은 이제 부채 자체가 든다 — 지울 과거 날이 없으면 API가 400이고, 오늘 몫은 애초에
-- 용서 대상이 아니라 광고를 무한히 봐도 "이미 밀린 날들을 지우는 것"을 넘어설 수 없다.
--
-- (user, waived_date) 유니크는 남긴다: 같은 날 중복 소거 방지 + 동시 요청(두 탭) race의 최종 방어.
-- granted_on 컬럼 자체는 "언제 지급됐나" 기록으로 유지한다(유니크만 제거).
--
-- 문법: alter ... drop constraint 는 MySQL(운영)·H2 MySQL 모드(FlywayMigrationTest) 양쪽에서 동작한다
-- (V14__drop_nickname_unique.sql 선례). H2 메인 스위트는 Hibernate가 스키마를 만들어 이 제약이 애초에 없다.

alter table reading_goal_waiver drop constraint uk_goal_waiver_grant;
