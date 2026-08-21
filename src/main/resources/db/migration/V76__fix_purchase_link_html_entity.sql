-- V76 — 이미 저장된 구매링크의 &amp; 를 & 로 되돌린다(제휴 추적 복구).
--
-- 알라딘 ItemSearch/ItemLookUp이 JSON의 link 값만 HTML 엔티티로 인코딩해 주는데, 파싱이 그대로
-- 저장해 왔다(AladinBookSearchClient.unescapeAmp 가 이번에 막았지만 그건 앞으로 담길 책 얘기다).
-- 그렇게 저장된 링크는 알라딘이 파라미터를 amp;ttbkey·amp;partner 로 읽어 제휴 식별에 실패한다 —
-- 상품 페이지는 정상으로 뜨므로 화면상 증상이 없고 수수료만 0이 된다(귀속 쿠키 대조로 확인).
--
-- 백필이 이 문제를 못 걷어낸다: BookRepository 의 백필 대상 조회는 'ttbkey=' 문자열 포함 여부로
-- "이미 제휴링크가 실렸다"를 판정하는데, &amp;ttbkey= 에도 그 문자열이 들어 있어 전부 건너뛴다.
-- 그래서 일괄 치환이 필요하다.
--
-- 알라딘 링크로 한정하지 않는 이유: purchase_link 에는 수동 입력값도 들어올 수 있으나, URL 쿼리에
-- 리터럴 &amp; 가 의도된 경우는 없다(그런 URL은 어느 사이트에서도 이미 깨진 것이다). LIKE 로 대상만
-- 좁혀 불필요한 쓰기를 피한다. 멱등이라 재실행해도 안전하다.
update book
set purchase_link = replace(purchase_link, '&amp;', '&')
where purchase_link like '%&amp;%';
