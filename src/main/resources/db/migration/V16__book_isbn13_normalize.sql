-- V16 — 기존 book.isbn13 정규화(백필).
--
-- 적재 시점 정규화(Isbn.normalize, Book 생성자)는 이제부터 들어오는 책에만 적용된다.
-- 이미 저장된 행은 표기가 갈려 있을 수 있어(하이픈 포함 / 빈 문자열 "") 인기 카운트의 group by 키가
-- 오염된다 — 같은 책이 하이픈 유무로 쪼개지고, ISBN 없는 서로 다른 책이 ""로 뭉친다.
--
-- 그래서 기존 행도 같은 규칙으로 모은다: 하이픈·공백 제거 후, 알맹이가 없으면(빈 값) NULL로.
-- nullif/replace/trim은 MySQL·H2 공통 — 테스트(H2)와 운영(MySQL) 모두에서 동일하게 동작한다.
update book
   set isbn13 = nullif(trim(replace(replace(isbn13, '-', ''), ' ', '')), '')
 where isbn13 is not null;
