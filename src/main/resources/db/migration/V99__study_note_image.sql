-- V99 — 공부 필기 사진.
--
-- 행 1개 = 필기에 넣은 사진 한 장(브라우저가 긴 변 1568px JPEG로 줄인 바이트). 필기 본문은 마크다운
-- `![](/api/study/notes/images/{id})` 한 꼴로만 가리킨다.
-- 사진은 **필기가 아니라 사람**에게 붙는다(user_id FK 하나, note_id 없음) — 필기 행은 첫 비공백 저장 때 생기는데 사진은
-- 그보다 먼저 올라오고, 되돌리기·장 사이 복사로 다른 장이 같은 사진을 다시 가리킬 수 있다.
-- 어느 필기도 안 가리키는 사진은 매일 04:50 KST 정리(StudyNoteImageSweeper)가 orphaned_at을 찍고 다음 밤 지운다.
--
-- ① data는 mediumblob(최대 16MB)이다 — blob(64KB)이면 운영에서만 「Data too long」이 난다. H2(MODE=MySQL)는 둘을 똑같이
--    BINARY LARGE OBJECT로 보고해 테스트가 이 차이를 못 잡는다(설계 원장 U-1이 mysql:8.4에서 잰다).
--    엔티티는 @Lob byte[]가 맞다 — H2가 Types.BLOB(2004)으로 보고해 validate와 맞는다(V85·V89의 「@Lob 금지」는 text 이야기다).
-- ② byte_size는 사람당·서비스 전체 용량 합계를 바이트를 읽지 않고 더하려는 사본이다.
-- ③ 매일 mysqldump에 그대로 실린다 — backup-mysql.sh가 --hex-blob과 /var/tmp를 쓰는 이유.
--    운영 MySQL은 바이너리 로그를 끈다(compose.prod.yaml --disable-log-bin) — 켜 두면(8.x 기본 FULL·30일) 사진 한 장이
--    INSERT·정리 UPDATE 앞뒤·DELETE로 binlog에 약 4번 실려 30일 남아, 사람당·전체 상한이 디스크를 못 묶는다.
-- ④ FK는 NO ACTION — 탈퇴는 AccountService.purge가 벌크로 지운다(FlywayMigrationTest가 목록을 대조한다).
--    user_id 인덱스는 FK가 스스로 만든다(MySQL·H2) — 따로 만들지 않는다. V1 제약 이름을 참조하지 않는다(FlywayMigrationTest REQ-03).
create table study_note_image (
    id          bigint       not null auto_increment,
    user_id     bigint       not null,
    byte_size   int          not null,
    data        mediumblob   not null,
    created_at  datetime(6)  not null,
    orphaned_at datetime(6),
    primary key (id),
    constraint fk_study_note_image_user foreign key (user_id) references users (id)
);
