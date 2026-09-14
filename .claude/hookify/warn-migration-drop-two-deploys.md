---
name: warn-migration-drop-two-deploys
enabled: true
event: file
action: warn
path-pattern: db[/\\]migration[/\\]V\d+__.*\.sql$
pattern: (?i)(alter\s+table\s+\S+\s+drop\s+(column\s+)?(?!(index|key|foreign|constraint|primary|check)\b)\w+|drop\s+table)
---
⚠️ 마이그레이션이 **컬럼·테이블을 drop**한다. 그 컬럼·테이블을 매핑하던 **코드 삭제가 이미 배포돼 운영 컨테이너에 떠 있는가?** (T-245, V88 선례)

**같은 PR에 엔티티 필드 삭제와 이 drop이 함께 있으면 drop을 떼어 다음 배포로 보낸다.**

- 배포는 블루/그린이다(`deploy/deploy-on-ec2.sh`) — 옛 컨테이너가 트래픽을 받는 채로 새 컨테이너가 뜨며 **Flyway가 drop을 실행**하고, 새 컨테이너 헬스 통과 뒤에야 옛 것을 내린다.
- 그 창 동안 옛 이미지의 엔티티가 없어진 컬럼을 SELECT → `Unknown column` → **인증이 걸린 모든 요청 500**. readiness(`SELECT 1`)는 통과해 Caddy가 옛 컨테이너를 안 뺀다.
- 코드 삭제 PR에서 컬럼을 남겨 두는 건 안전하다 — `NOT NULL`이면 `DEFAULT`가 있어야 새 코드의 INSERT가 산다(없으면 그 PR에서 DEFAULT부터 준다).
- drop PR 착수 게이트: 코드 삭제 PR 배포 뒤 **실행 중인 앱 컨테이너 이미지 = 그 머지 커밋 이미지**(ECR `imageTag=<sha>` 다이제스트 대조).
- 롤백 주의: drop 뒤 코드 삭제 **이전** 이미지로 되돌리면 기동·readiness는 통과하고 인증 요청만 500이 난다(V88 파일 주석).
- 컬럼 부재는 `FlywayMigrationTest`에 `INFORMATION_SCHEMA` 단언으로 못 박는다 — `ddl-auto=validate`는 여분 컬럼을 통과시켜 drop이 빠져도 초록이다.
