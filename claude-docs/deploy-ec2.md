# AWS 배포 가이드 — EC2 단일 인스턴스 (현행)

> BookTimer 운영 배포의 **현행 정본**. 구 아키텍처(ECS Fargate + ALB + RDS)는
> [deploy-aws.md](deploy-aws.md) 참고 — 컷오버 완료 후 폐기 예정.
>
> **왜 옮겼나**: 크레딧이 2026-07에 고갈되며 실사용 원가(월 ~$100)가 드러났다. 수입 0인 개인
> 서비스가 HA(다중 AZ)에 월 $50을 쓰고 있던 셈이라, HA를 내려놓고 **배포 무중단만 유지**하는
> 단일 인스턴스로 합쳤다. 월 ~$30.

## 아키텍처

```
인터넷 → EIP → [ EC2 t3.small (2GB, AL2023) ]
                 └ Caddy 2      : 443 TLS(Let's Encrypt 자동) + 호스트 정규화 301
                 │   └ reverse_proxy app-blue:8080 app-green:8080  (active health check)
                 └ app-blue | app-green : Spring Boot (한 번에 하나만 실행)
                 └ mysql:8.4   : EBS 볼륨
                 └ cron 03:00 KST : mysqldump → S3
```

- **시크릿**: SSM Parameter Store `/booktimer/*` (ECS 시절 그대로 재사용) → `render-env.sh`가 `.env` 생성
- **배포**: GitHub Actions → SSM Send-Command → `deploy-on-ec2.sh` (SSH 키 없음)
- **무중단**: Caddy가 두 upstream을 헬스체크로 지켜본다. 배포 시 Caddy 설정을 건드리지 않는다.

## 파일

| 파일 | 역할 |
|---|---|
| `deploy/compose.prod.yaml` | 운영 스택 전체 (루트 `compose.yaml`은 로컬 개발용) |
| `deploy/caddy/Caddyfile` | TLS·라우팅·301·forwarded 헤더 신뢰 경계 (테스트: `deploy/tests/test-caddy-forwarded.sh`). ⚠️ **디렉터리(`deploy/caddy/`)째 마운트한다** — 파일 하나를 마운트하면 배포의 rename 교체가 컨테이너에 반영되지 않아 설정 변경이 조용히 죽는다(T-167) |
| `deploy/deploy-on-ec2.sh` | blue-green 전환 (테스트: `deploy/tests/test-deploy-bluegreen.sh`) |
| `deploy/render-env.sh` | SSM → `.env`. **SSM 이름 ↔ 앱 환경변수 매핑의 단일 출처** |
| `deploy/bootstrap-ec2.sh` | 최초 1회 셋업 (docker·스왑·로그로테이션·cron) |
| `deploy/backup-mysql.sh` | 일일 백업 |

---

## 컷오버 절차

### A. 사전 준비 (무중단 — 기존 서비스는 계속 ECS가 처리)

1. **EC2 생성** — `t3.small` / Amazon Linux 2023 / gp3 30GB
2. **기존 유휴 EIP 연결** — `eipalloc-0458af41a95c1b108`. 새로 할당하지 말 것(이미 과금 중인 것을 회수)
3. **보안그룹** — 인바운드 80·443 = `0.0.0.0/0`. **SSH(22)는 열지 않는다** (접속은 SSM Session Manager)
4. **인스턴스 역할** — `AmazonSSMManagedInstanceCore` + ECR pull + SSM 파라미터 읽기 + 백업 버킷 S3 쓰기
   > ⚠️ `ssm:GetParametersByPath`는 **경로 리소스 자체**에도 권한이 필요하다. Resource에
   > `parameter/booktimer` 와 `parameter/booktimer/*` 를 **둘 다** 넣어야 한다 —
   > `/*` 만 주면 `AccessDenied`로 `render-env.sh`가 전량 실패한다(실제로 겪음).
5. **SSM 신규 파라미터** — `/booktimer/MYSQL_ROOT_PASSWORD` (SecureString). 없으면 `render-env.sh`가 누락으로 실패한다
6. **bootstrap 실행** — SSM Session Manager 접속 후 `sudo bash bootstrap-ec2.sh`
7. **RDS를 그대로 바라보게 하고 먼저 기동** — DB 이관 전에 앱만 검증한다
   ```bash
   cd /opt/booktimer && ./render-env.sh
   docker compose -f compose.prod.yaml up -d caddy mysql app-blue
   curl -H 'Host: booktimer.app' http://<EIP>/actuator/health   # {"status":"UP"} 기대
   ```
8. **Route53 A레코드 TTL을 60초로 미리 낮춤** (전파 대기 최소화)

### B. 컷오버 (다운타임 0)

9. **Route53 A레코드 4개를 전부 EIP로** — `booktimer.app`, `www.booktimer.app`,
   `booktimer.click`, `www.booktimer.click`.
   ⚠️ 4개 다 옮겨야 Caddy가 각 호스트 인증서를 발급받는다. **ALB·ECS는 그대로 둔다**(롤백 경로).
10. **검증** — HTTPS 4개 호스트, 로그인(폼·Google OAuth), 세션 유지, 정원/책장 주요 화면

### C. DB 이관 (다운타임 5~10분 — 트래픽 최저 시간대)

11. ECS 서비스 `desired-count 0` (쓰기 차단)
12. 덤프 → 임포트
    ```bash
    mysqldump -h <RDS엔드포인트> -u admin -p \
      --single-transaction --routines --triggers --databases booktimer > dump.sql
    docker compose -f compose.prod.yaml exec -T mysql mysql -uroot -p"$PW" < dump.sql
    ```
    ⚠️ `flyway_schema_history`가 포함됐는지 확인 — 빠지면 새 DB에서 마이그레이션이 재실행된다
13. **앱 DB 사용자를 직접 만든다 (필수 — 실제로 여기서 서비스가 내려갔다)**
    ```bash
    docker compose -f compose.prod.yaml exec -T mysql mysql -uroot -p"$ROOT_PASS" -e \
      "CREATE USER IF NOT EXISTS 'admin'@'%' IDENTIFIED BY '$DB_PASS';
       GRANT ALL PRIVILEGES ON booktimer.* TO 'admin'@'%'; FLUSH PRIVILEGES;"
    ```
    **`mysqldump`는 스키마와 데이터만 옮기고 계정(`mysql.user`)은 옮기지 않는다.** RDS의 앱 계정(`admin`)이
    새 MySQL엔 없으므로, 이 단계를 빼면 앱이 DB 접속에 실패해 기동하지 못한다.
14. SSM `/booktimer/SPRING_DATASOURCE_URL`을
    `jdbc:mysql://mysql:3306/booktimer?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC`로 `--overwrite`
    > ⚠️ `allowPublicKeyRetrieval=true&useSSL=false`를 빠뜨리지 말 것 — MySQL 8.4는 `caching_sha2_password`가
    > 기본이라 이게 없으면 드라이버가 공개키를 못 받아 접속이 거부될 수 있다(RDS URL에도 원래 붙어 있었다).
15. `./deploy-on-ec2.sh` 재실행 → 데이터 육안 검증(내 책장·측정 기록·로그인)

> ⚠️ **재배포 전에 기존 앱 컨테이너를 `rm` 하지 말 것.** blue-green의 안전망("헬스 실패 시 옛 컨테이너 유지")은
> 옛 컨테이너가 남아 있어야 작동한다. 미리 지우면 새 컨테이너가 실패했을 때 **되돌아갈 대상이 없어 서비스가
> 완전히 내려간다** — 실제 이관에서 13번을 빠뜨린 채 컨테이너를 먼저 지워 수 분간 503이 났다.
> 환경변수만 바뀐 경우 compose가 알아서 재생성하므로 `rm`은 불필요하다.

### D. 정리 ✅ 완료 (2026-08-03)

컷오버 후 엿새 안정화를 확인하고 구 리소스를 전부 삭제했다.

| 대상 | 결과 |
|---|---|
| ALB `booktimer-alb` · 리스너 · 타깃그룹 `booktimer-tg` | 삭제 ✅ |
| ECS 서비스 `booktimer-service` · 클러스터 `booktimer-cluster` | 삭제(INACTIVE) ✅ |
| RDS `booktimer-db` | 삭제 ✅ — 최종 스냅샷 `booktimer-db-final-20260803` 보존 |
| Application Auto Scaling 타깃 | 등록 해제 ✅ |
| `task-definition.json`·`autoscaling-config.yml`·`zero-downtime-config.yml` | 삭제 ✅ |
| `deploy.yml` SSM 전환 | ✅ 2026-07-28 — **리소스 삭제보다 먼저** 했다 |

> `deploy.yml` 전환을 먼저 한 이유: 미루면 그 사이 main push가 ECS에 배포돼 "성공"으로 끝나는데
> 실제 서비스(EC2)엔 반영되지 않는 **무성 실패**가 된다(desired=0이라 안정화가 즉시 성공해버린다).

**삭제 전 사전 점검** — 되돌릴 수 없으므로 넷 다 통과시키고 지웠다. 유사 작업 때 그대로 재사용할 것:

1. Route53 A레코드 4개가 전부 EIP를 가리키는가 (ALB alias가 아닌가)
2. `https://booktimer.app/actuator/health` 가 `200` `{"status":"UP"}` 인가
3. ECS 서비스 `desiredCount`·`runningCount` 가 모두 0인가
4. 타깃그룹에 등록된 타깃이 없는가 (`[]`)

> ⚠️ **백업 확인이 먼저다.** 이 정리에 착수했을 때 일일 백업이 **엿새간 0건**이었다(T-138).
> RDS를 지우면 S3 덤프가 유일한 백업이 되므로, **RDS 삭제 전에 백업 객체가 실제로 존재하는지
> `aws s3 ls` 로 눈으로 확인**한다. "cron이 걸려 있다"는 근거가 못 된다.

> ⏳ ALB 삭제는 **비동기**다. 삭제 직후 타깃그룹을 지우려 하면 `ResourceInUse`(리스너가 아직 정리 중)로
> 거부되니, 몇 분 뒤 재시도한다.

**롤백**: 🛑 **더 이상 없다.** ALB·ECS·RDS가 사라져 DNS를 되돌리는 경로가 없다. 남은 복구 수단은
RDS 최종 스냅샷(2026-08-03 시점)과 S3 일일 덤프(7일 보존)뿐이다.

---

## 실제 구축된 리소스 (2026-07-28 컷오버 완료)

| 리소스 | 식별자 |
|---|---|
| EC2 | `i-07a649585c25707a3` (t3.small, `ap-northeast-2b` — RDS와 동일 AZ) |
| EIP | `15.165.95.129` / `eipalloc-0458af41a95c1b108` (기존 유휴 EIP 회수) |
| 보안그룹 | `sg-026943267829a42aa` (80·443 인바운드, SSH 미개방) |
| IAM | 역할 `booktimerEc2Role` / 프로파일 `booktimerEc2Profile` |
| S3 | `booktimer-ops-459338751419` (`deploy/` 배포자산 · `mysql/` 백업 7일 보존) |
| Route53 | `.app` `Z0795663J1W7C48SU27B` · `.click` `Z0571153WI2EVDRD8ZTY` |

~~**롤백 정보**(ALB가 살아있는 동안 유효)~~ — 🛑 **무효**: ALB를 2026-08-03에 삭제해
"A레코드를 ALB alias로 되돌리는" 경로는 사라졌다. 위 「D. 정리」의 롤백 항목 참고.

> ⚠️ **AWS CLI를 이 PC에서 쓸 때는 T-136**(cp949 인코딩)을 먼저 볼 것 — Git Bash + `PYTHONUTF8=1
> LC_ALL=C.UTF-8`, 입력 JSON은 ASCII, 경로는 `file://C:/...`. 안 지키면 명령은 성공하는데
> 결과를 못 읽거나 입력이 거부된다.

## 알려진 한계

- **단일 장애점**: 인스턴스가 죽으면 서비스가 내려간다(재부팅까지 수 분). 의도적 트레이드오프 —
  HA를 복원하려면 EC2 2대 + ALB가 필요해 이전 비용으로 돌아간다.
  완화: CloudWatch `StatusCheckFailed` 알람 + EC2 자동 복구 액션.
- **배포 시 in-flight 1건**: 옛 컨테이너를 내리는 순간 처리 중이던 요청 1건 정도가 끊길 수 있다
  (로컬 150요청 프로브 실측). 이미 서버에 도달한 요청이라 LB 재시도로 못 막는다.
  0건으로 만들려면 Caddy admin API로 "먼저 빼고 나중에 죽이는" 순서가 필요한데, 복잡도 대비
  이득이 작다고 판단해 채택하지 않았다.
- **백업은 논리 덤프뿐**: RDS 자동 백업(PITR)이 사라졌다. 복구 입도는 최대 24시간.
  세션 테이블(`SPRING_SESSION`·`SPRING_SESSION_ATTRIBUTES`)은 **데이터를 백업에서 제외**한다 —
  휘발성이라 복원해도 의미가 없는데 덤프의 ~95%를 차지해 진짜 데이터의 크기 변화를 가렸다.
  다만 빈 테이블 **스키마는 보존**한다(구조까지 빼면 복원본의 `flyway_schema_history`와 어긋나
  마이그레이션이 재실행되지 않아 앱이 세션 테이블 부재로 기동 실패한다).
