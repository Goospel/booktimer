-- V102 — 책생글 로그인 넓히기(PR-M): users.kakao_sub · users.google_sub.
--
-- kakao_sub: 앱 카카오 로그인 신원(ID 토큰의 sub = 회원번호 숫자 문자열). apple_sub(V100)와 같은 꼴 — NULL이면 미연결,
--   붙으면 once-set. 한 신원이 두 계정에 붙지 않게 UNIQUE가 최종 방어선이다(NULL 여러 개는 허용 — MySQL·H2 같음).
-- google_sub: 구글 신원 고정(REQ-115) — 처음 구글로 열린 계정에 그 구글 계정의 sub를 박는다. 구글 문서의 sub 상한이
--   「대소문자를 가리는 ASCII 최대 255자」라 Apple · 카카오의 64와 다르다. 쓰기는 UserRepository.pinGoogleSubIfEmpty
--   (비어 있을 때만 쓰는 조건부 UPDATE) 하나뿐이고 해제는 운영 SQL이다. 백필은 없다 — 채울 원천이 없다.
--
-- auth_provider에 'KAKAO'가 들어갈 자리는 이미 varchar(20)이다(V100 머리 주석 — 2026-10-21 운영 실측). MODIFY 없음.
alter table users add column kakao_sub varchar(64) null;
create unique index uk_users_kakao_sub on users (kakao_sub);

alter table users add column google_sub varchar(255) null;
create unique index uk_users_google_sub on users (google_sub);
