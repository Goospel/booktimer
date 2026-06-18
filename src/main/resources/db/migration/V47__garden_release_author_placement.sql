-- C2 풀어놓기: 작가 캐릭터(AUTHOR)를 좌표 배치에서 배회로 전환.
-- ownedPlants()에서 AUTHOR를 제거했으므로 layoutOf() 교집합이 이미 이 행들을 렌더에서 제외하지만,
-- stale 데이터 정리 차원에서 삭제한다.
DELETE FROM garden_placement WHERE axis = 'AUTHOR';
