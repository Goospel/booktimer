-- V92 — 공부 「하루 목표」 컬럼 삭제(V79가 만든 users.study_daily_goal_seconds).
--
-- 책별 「회당 시간」(study_book.session_goal_seconds, V90)이 대체했다. 웹·미니앱 모두 이 값을 읽지도 쓰지도
-- 않고, 그걸 담은 미니앱 번들 20260914-149가 라이브가 된 뒤 지운다(V90 주석의 「별도 마이그레이션」이 이것).
-- 코드는 직전 배포(#1132)에서 이미 걷혔다 — 이 파일은 그 뒤에 따로 배포한다(옛 컨테이너가 이 컬럼을
-- SELECT 하는 채로 drop 하면 블루/그린 전환 중 인증 요청이 전부 500, V88과 같은 두 단계 · T-245).
-- 저장돼 있던 값은 함께 사라진다 — 사용자 동의가 끝난 결정이다.
alter table users drop column study_daily_goal_seconds;
