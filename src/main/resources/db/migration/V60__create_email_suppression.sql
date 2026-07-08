-- V60 — 이메일 억제 목록(bounce/complaint) — SES 프로덕션 액세스 승인 후 발신 평판 보호.
--
-- SES 아이덴티티 알림(SNS)으로 받은 영구 반송·수신자 불만 주소를 앱 레벨에서 억제한다.
-- 발송 전 이 목록을 조회해 억제된 주소로의 재발송을 막아, 반송·불만 누적에 의한 발신 도메인
-- 평판 하락을 예방한다(AWS 승인문 요구: "Set up a process to handle bounces and complaints").
-- email UNIQUE로 멱등 보장. reason=BOUNCE|COMPLAINT. datetime(6)은 MySQL·H2 동일(V31 관례).

create table email_suppression (
    id          bigint       not null auto_increment,
    email       varchar(255) not null,
    reason      varchar(20)  not null,
    detail      varchar(255),
    created_at  datetime(6)  not null,
    primary key (id),
    constraint uk_email_suppression_email unique (email)
);
