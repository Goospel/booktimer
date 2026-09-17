-- V93 — 공부 항목의 바로가기 링크(인강 페이지 등). 식별자가 아니다 — 죽어도 제목·시간·필기·잔디는 무관.
-- http/https만 저장된다(StudyBook.normalizeLinkUrl). 종류 컬럼은 두지 않는다(설계 2026-09-16 D1).
alter table study_book add column link_url varchar(1000) null;
