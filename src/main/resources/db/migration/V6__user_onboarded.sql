-- V6 — 온보딩(첫 진입 초기 설정) 완료 여부 플래그.
-- 신규 가입자는 false로 시작해 온보딩 페이지로 유도되고, 완료하면 true가 된다.
-- 기존 사용자는 이미 서비스를 쓰고 있으므로 true로 백필해 온보딩을 강요받지 않게 한다.
-- boolean은 MySQL(tinyint(1))·H2 양쪽에서 동일하게 동작한다(V1 enum→varchar 방침과 별개로 이식 안전).

alter table users add column onboarded boolean not null default false;
update users set onboarded = true;
